(ns circle.web.views.github-commit-hook
  (:require [org.danlarkin.json :as json])
  (:use noir.core
        hiccup.core
        hiccup.page-helpers
        [noir.request :only (*request*)])
  (:require [circle.backend.build :as build])
  (:require [circle.backend.email :as email])
  (:require [circle.backend.project.circle :as circle])
  (:use [circle.backend.build :only (extend-group-with-revision)])
  (:use [clojure.tools.logging :only (infof)])
  (:use circle.web.views.common))

(def sample-json (json/decode "{
  \"before\": \"5aef35982fb2d34e9d9d4502f6ede1072793222d\",
  \"repository\": {
    \"url\": \"http://github.com/defunkt/github\",
    \"name\": \"github\",
    \"description\": \"You're lookin' at it.\",
    \"watchers\": 5,
    \"forks\": 2,
    \"private\": 1,
    \"owner\": {
      \"email\": \"chris@ozmm.org\",
      \"name\": \"defunkt\"
    }
  },
  \"commits\": [
    {
      \"id\": \"41a212ee83ca127e3c8cf465891ab7216a705f59\",
      \"url\": \"http://github.com/defunkt/github/commit/41a212ee83ca127e3c8cf465891ab7216a705f59\",
      \"author\": {
        \"email\": \"chris@ozmm.org\",
        \"name\": \"Chris Wanstrath\"
      },
      \"message\": \"okay i give in\",
      \"timestamp\": \"2008-02-15T14:57:17-08:00\",
      \"added\": [\"filepath.rb\"]
    },
    {
      \"id\": \"de8251ff97ee194a289832576287d6f8ad74e3d0\",
      \"url\": \"http://github.com/defunkt/github/commit/de8251ff97ee194a289832576287d6f8ad74e3d0\",
      \"author\": {
        \"email\": \"chris@ozmm.org\",
        \"name\": \"Chris Wanstrath\"
      },
      \"message\": \"update pricing a tad\",
      \"timestamp\": \"2008-02-15T14:36:34-08:00\"
    }
  ],
  \"after\": \"de8251ff97ee194a289832576287d6f8ad74e3d0\",
  \"ref\": \"refs/heads/master\"
}"))

(defn email-subject [build-result]
  (if (build/successful? build-result)
    "Build Success"
    "FAIL"))

(defn success-email [build build-result]
  (str "Build of" (-> build :vcs-revision) "successful"))

(defn fail-email [build build-result]
  (str "Build of" (-> build :vcs-revision) "failed" (map #(str (:out %) (:err %)) (-> build-result :action-results))))

(defn email-body [build build-result]
  (if (build/successful? build-result)
    (success-email build build-result)
    (fail-email build build-result)))

(defn process-json [github-json]
  (def last-json github-json)
  (when (= "CircleCI" (-> github-json :repository :name))
    (let [build (extend-group-with-revision
                  (merge circle/circle-deploy
                         {:vcs-type :git
                          :vcs-url (-> github-json :repository :url)
                          :vcs-revision (-> github-json :commits last :id)
                          :num-nodes 1}))
          _ (infof "process-json: build=" build)
          result (build/run-build build)]
      (email/send :to (-> github-json :owner :email)
                  :subject (email-subject result)
                  :body (email-body build result)))))

(defpage [:post "/github-commit"] []
  (infof "github post: %s" *request*)
  (def last-request *request*)
  (let [github-json (json/decode (-> *request* :params :payload))]
    (future (process-json github-json))
    {:status 200 :body ""}))