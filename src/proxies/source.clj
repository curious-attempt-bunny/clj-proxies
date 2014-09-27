(ns proxies.source
    (:require [net.cgrand.enlive-html :as enlive])
    (:require [clojure.core.async :as async :refer [chan >! <!! go]])
    (:require [clj-http.client :as client]))

(def proxies-list (ref nil))

(def proxy-regex #"PROXY_IP.*?([0-9]+\.[0-9]+\.[0-9]+\.[0-9]+).*?PROXY_PORT.*?([0-9]+)")

(defn page-proxies
    [page]
        (->>
            (enlive/select page [:script])
            (map enlive/text)
            (map #(re-find proxy-regex %))
            (remove nil?)
            (map #(hash-map :proxy-host (second %) :proxy-port (Integer/parseInt (last %))))
            ))

(defn fetch
    [url]
    (enlive/html-resource (java.net.URL. url)))

(def vet-options {:conn-timeout 10000 :socket-timeout 15000 :retry-handler (fn [& _] false)})

(def vet-url "https://www.google.com/")

(defn vet-response
    [p]
    (client/get vet-url (merge vet-options p)))

(defn vet-proxy
    [p]
    (try
        (if (= 200 (->> p (vet-response) (:status))) p)
        (catch Exception _ nil)))

(defn vet-proxies
    [ps]
    (->> ps
        (pmap vet-proxy)
        (remove nil?)))

(defn proxies
    []
    (if (nil? @proxies-list)
        (dosync
            (if (nil? @proxies-list)                
                (ref-set proxies-list
                    (->> "http://gatherproxy.com/proxylist/country/?c=United%20States"
                        (fetch)
                        (page-proxies)
                        (shuffle)
                        (vet-proxies))))))
    @proxies-list)

(defn get-proxy
    []
    (dosync
        (let [ps (proxies)
              p  (first ps)]
            (ref-set proxies-list
                (concat (rest ps) (list p)))
            (prn p)
            p)))

(defn- wrap-method
    [method url req]
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
        (prn (proxies))
        ; (prn (get "https://www.google.com/"))
        (println (- (System/currentTimeMillis) start))))
