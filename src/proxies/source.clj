(ns proxies.source
    (:require [net.cgrand.enlive-html :as enlive])
    (:require [clojure.core.async :as async :refer [chan >! <!! go]]))

(def proxy-regex #"^([0-9]+\.[0-9]+\.[0-9]+\.[0-9]+:[0-9]+)$")

(defn page-proxies
    [page]
        (->>
            (enlive/select page [:li.proxy])
            (map enlive/text)
            (map #(re-matches proxy-regex %))
            (filter identity)
            (map first)))

(defn pages
    [main-page]
    (->>
        (enlive/select main-page [:div.table-menu :a.item])
        (map #(get-in % [:attrs :href]))
        (map #(clojure.string/replace % #"^\." "http://www.proxy-list.org/en"))))

(defn fetch
    [url]
    (enlive/html-resource (java.net.URL. url)))

(defn proxies
    []
    (let [main-page (fetch "http://www.proxy-list.org/en/index.php")
          sub-pages (pages main-page)
          c         (chan (count sub-pages))
          coll      (atom [])]
        (doseq [page sub-pages]
            (go (>! c (page-proxies (fetch page)))))
        (doseq [_ sub-pages]
            (swap! coll concat (<!! c)))
        @coll))

(defn main
    []
    (let [start (System/currentTimeMillis)]
        (proxies)
        (println (- (System/currentTimeMillis) start))))
