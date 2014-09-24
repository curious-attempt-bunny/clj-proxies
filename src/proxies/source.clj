(ns proxies.source
    (:require [net.cgrand.enlive-html :as enlive]))

(def proxy-regex #"^([0-9]+\.[0-9]+\.[0-9]+\.[0-9]+:[0-9]+)$")

(defn proxies
    [page]
        (->>
            (enlive/select page [:li.proxy])
            (map enlive/text)
            (map #(re-matches proxy-regex %))
            (filter identity)))

(defn main
    []
    (let [main-page (enlive/html-resource (java.net.URL. "http://www.proxy-list.org/en/index.php"))]
        (prn (proxies main-page))))
