(ns spotify-dj-info.env
  (:require [clojure.string :as str]))

(defn getenv [key]
  (System/getenv key))
