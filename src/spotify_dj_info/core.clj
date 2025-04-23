(ns spotify-dj-info.core
  (:require [clj-http.client :as http]
            [cheshire.core :as json]
            [ring.adapter.jetty :as jetty]
            [ring.util.response :as resp]
            [ring.middleware.params :refer [wrap-params]]))


;; AUTH STUFF
(def SPOTIFY_CLIENT_ID "b2feeda8fa1641d7acfef0f6a9e927e3")
(def SPOTIFY_CLIENT_SECRET "7c43093ce678468a8dc295dca92ab0e4")
(def REDIRECT_URI "http://localhost:3000/callback")

(defn auth-url []
  (str "https://accounts.spotify.com/authorize"
       "?client_id=" SPOTIFY_CLIENT_ID
       "&response_type=code"
       "&redirect_uri=" (java.net.URLEncoder/encode REDIRECT_URI "UTF-8")
       "&scope=" (java.net.URLEncoder/encode "playlist-read-private user-library-read" "UTF-8")))

(defn exchange-token [code]
  (http/post "https://accounts.spotify.com/api/token"
             {:form-params {:grant_type "authorization_code"
                            :code code
                            :redirect_uri REDIRECT_URI
                            :client_id SPOTIFY_CLIENT_ID
                            :client_secret SPOTIFY_CLIENT_SECRET}
              :as :json}))


(defn callback-handler [req]
  (let [code (get-in req [:query-params "code"])]
    (if code
      (let [resp (exchange-token code)
            token-data (:body resp)]
        (spit "auth.json" (json/generate-string token-data {:pretty true}))
        (resp/response "✅ Token saved. You can close this window."))
      (resp/response "❌ Error: Missing code in callback."))))

(defn refresh-token! []
  (let [refresh-token (:refresh_token (json/parse-string (slurp "auth.json") true))
        response (http/post "https://accounts.spotify.com/api/token"
                            {:form-params {:grant_type "refresh_token"
                                           :refresh_token refresh-token
                                           :client_id SPOTIFY_CLIENT_ID
                                           :client_secret SPOTIFY_CLIENT_SECRET}
                             :as :json})]
    (let [token (:body response)]
      ;; update access_token in saved file
      (str token))))

(defn -main [& _]
  (let [url (auth-url)]
    (println "\n🚀 COPY AND PASTE THIS URL INTO YOUR BROWSER TO AUTHORIZE SPOTIFY:")
    (println url)
    (println "\nWaiting for callback at http://localhost:3000/callback...\n"))
  (jetty/run-jetty
   (wrap-params
    (fn [req]
      (case (:uri req)
        "/callback" (callback-handler req)
        (resp/not-found "Not Found"))))
   {:port 3000}))

;; LOGIC

(defn extract-playlist-id [url]
  (let [pattern #"playlist/([a-zA-Z0-9]+)"
        matcher (re-matches pattern (second (re-find pattern url)))]
    (second (re-find pattern url))))

(defn get-playlist-tracks [playlist-link access-token]
  (let [playlist-id (extract-playlist-id playlist-link)
        base-url (str "https://api.spotify.com/v1/playlists/" (str playlist-id) "/tracks")]
    (loop [url base-url
           all-tracks []]
      (let [response (http/get url
                               {:headers {"Authorization" (str "Bearer " access-token)}
                                :as :json})
            items (:items (:body response))
            next-url (get-in response [:body :next])
            ids (map #(get-in % [:track :id]) items)]
        (if next-url
          (recur next-url (concat all-tracks ids))
          (vec (concat all-tracks ids)))))))

;; Get info
(defn chunked [coll n]
  (partition-all n coll))

(defn get-audio-features-for-tracks [track-ids access-token]
  (let [chunks (chunked track-ids 100)]
    (->> chunks
         (mapcat
           (fn [id-group]
             (let [url (str "https://api.spotify.com/v1/audio-features?ids=" (clojure.string/join "," id-group))
                   resp (http/get url
                                  {:headers {"Authorization" (str "Bearer " access-token)}
                                   :as :json})]
               (:audio_features (:body resp)))))
         vec)))

(defn get-audio-features [track-id access-token]
  (-> (http/get (str "https://api.spotify.com/v1/audio-features/" track-id)
                {:headers {"Authorization" (str "Bearer " access-token)}
                 :as :json})
      :body))



(comment
  (def bash-script "env $(cat .env | xargs) clj -M -m spotify-dj-info.core")
  (def token (refresh-token!))
(extract-playlist-id "")
(auth-url)
  (get-playlist-tracks "https://open.spotify.com/playlist/0DVvT9FQOnVfTZxjPJOfl5?si=db313297f5d443bf"  token)

(def code "AQDBnxC0GFUvNHgAv7js16VDLGE38oulxZifA36M6Ph6NkZ8ZE5Y24GLd-KM7w5X4TFSnCBHStejkOE5ckjwG_dzBmEadufBNXogKgdl_eMFIYs7iVhbkwup1R4WBb0FZa7ejeFONk__ZMfYPVtuyJ28Wu1APtvbPeahsLhSsmS2LJWJifnrZjrrbQi613V2oGSCKE7q7t7TaDTeLkPp5efYWak2TVWhk5aeew")

(def pl "https://open.spotify.com/playlist/0DVvT9FQOnVfTZxjPJOfl5?si=db313297f5d443bf")
  
  )


