(ns proxies.source
    (:require [net.cgrand.enlive-html :as enlive])
    (:require [clojure.core.async :as async :refer [chan >! <!! go]])
    (:require [clj-http.client :as client]))

(def proxy-regex #"^([0-9]+\.[0-9]+\.[0-9]+\.[0-9]+:[0-9]+)$")

(defn page-proxies
    [page]
        (->>
            (enlive/select page [:li.proxy])
            (map enlive/text)
            (map #(re-matches proxy-regex %))
            (filter identity)
            (map first)
            (map #(clojure.string/split % #":"))
            (map #(hash-map :proxy-host (first %) :proxy-port (Integer/parseInt (second %))))))

(defn pages
    [main-page]
    (->>
        (enlive/select main-page [:div.table-menu :a.item])
        (map #(get-in % [:attrs :href]))
        (map #(clojure.string/replace % #"^\." "http://www.proxy-list.org/en"))))

(defn fetch
    [url]
    (enlive/html-resource (java.net.URL. url)))

(def proxies-list (ref nil))

(defn proxies
    []
    (if (nil? @proxies-list)
        (dosync
            (if (nil? @proxies-list)                
                (let [main-page (fetch "http://www.proxy-list.org/en/index.php")
                      sub-pages (pages main-page)
                      c         (chan (count sub-pages))
                      coll      (atom [])]
                    (go (>! c (page-proxies main-page)))
                    (doseq [page sub-pages]
                        (go (>! c (page-proxies (fetch page)))))
                    (dotimes [n (inc (count sub-pages))]
                        (swap! coll concat (<!! c)))
                    (ref-set proxies-list (shuffle @coll))))))
    (prn (count @proxies-list))
    @proxies-list)

(defn get-proxy
    []
    (dosync
        (let [ps (proxies)
              p  (first ps)]
            (ref-set proxies-list
                (concat (rest ps) (list p)))
            p)))

(defn- wrap-method
    [method url req]
    (prn "wrap-method" method url req)
    (apply method (list url (merge req (get-proxy)))))

(defn head    [url & [req]] (wrap-method client/head    url req))
(defn get     [url & [req]] (wrap-method client/get     url req))
(defn post    [url & [req]] (wrap-method client/post    url req))
(defn put     [url & [req]] (wrap-method client/put     url req))
(defn delete  [url & [req]] (wrap-method client/delete  url req))
(defn options [url & [req]] (wrap-method client/options url req))
(defn copy    [url & [req]] (wrap-method client/copy    url req))
(defn move    [url & [req]] (wrap-method client/move    url req))
(defn patch   [url & [req]] (wrap-method client/patch   url req))

(defn request
    [req]
    (client/request (merge req (get-proxy))))

(defn main
    []
    (let [start (System/currentTimeMillis)]
        (prn (get "https://www.google.com/"))
        (println (- (System/currentTimeMillis) start))))
