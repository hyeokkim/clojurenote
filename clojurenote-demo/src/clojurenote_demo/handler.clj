(ns clojurenote-demo.handler
  (:use compojure.core)
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.util.response :as resp]
            [environ.core :as environ]
            [clojurenote.auth :as auth]
            ))

; Sample ~/.lein/profiles.clj, or just overwrite calls to environ/env below with your values
; For more about environ/env, see https://github.com/weavejester/environ
{:user { :env {
  :clojurenote-demo-key "YOUR-KEY"
  :clojurenote-demo-secret "YOUR-SECRET"
}}}

(defn config []
  {
    :key (environ/env :clojurenote-demo-key)
    ; Or use :key "YOUR-KEY"
    :secret (environ/env :clojurenote-demo-secret)
    ; Or use :secret "YOUR-SECRET"
    ; Change this if not running on port 3000
    :callback "http://localhost:3000/evernote-oauth-callback"
    ; Delete this, or set to false, to run vs production Evernote server
    :use-sandbox true
  })

(defn login-evernote 
  "Obtain request token, put it in session, and redirect to the URL we're given"
  []
  (let [{url :url :as request-token} (auth/obtain-request-token (config))]
      (-> url
        (resp/redirect) 
        (assoc-in [:session :request-token] request-token))))

(defn on-successful-login [t]
  (str "<html><body>
          <p>Successfully logged into Evernote.</p>
          <p>User access token is " (:access-token t) "</p>
          <p>Full user details are as follows:</p>
          <code>" t "</code
          </body></html>"
  ))

(defn evernote-oauth-callback 
  "Given a verifier code from URL, and request token from session, obtain access token.
    Check that there's been no token weirdness by comparing oauth-token on URL 
    with request token in session"
  [{:keys [oauth_verifier oauth_token] :as params} {:keys [request-token]}]
  (if (= oauth_token (:token request-token))
    (->
      (auth/obtain-access-token (config) oauth_verifier request-token)
      (on-successful-login))
    (throw (Exception. 
      "ERROR - OAuth token on callback, and request token in session, did not match"))
  ))

(defroutes app-routes
  (GET "/" [] "<html><body>
          <form method='post' action='/login-evernote'>
            <button type='submit'>Login to Evernote</button>
          </form>
          </body></html>")

  (POST "/login-evernote" [] (login-evernote))

  (GET "/evernote-oauth-callback" {:keys [params session]} 
    (evernote-oauth-callback params session))

  (route/not-found "Not Found"))

(def app
  (handler/site app-routes))