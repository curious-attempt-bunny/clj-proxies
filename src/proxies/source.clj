(ns proxies.source
    (:require [net.cgrand.enlive-html :as enlive]))

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
    (let [main-page (fetch "http://www.proxy-list.org/en/index.php")]
        (concat (page-proxies main-page)
            (->>
                (pages main-page)
                (map fetch)
                (map #(page-proxies %))
                (apply concat)))))

(defn main
    []
    (println (clojure.string/join "\n" (proxies))))