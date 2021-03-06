(ns frontend.components.test-documentation
  (:require [cemerick.cljs.test :as t]
            [dommy.core :as dommy :refer-macros [sel1]]
            [frontend.test-utils :as test-utils]
            [frontend.components.documentation :as documentation]
            [frontend.utils.docs :as doc-utils]
            [frontend.stefon :as stefon]
            [goog.dom]
            [om.core :as om :include-macros true])
  (:require-macros [cemerick.cljs.test :refer (is deftest with-test run-tests testing test-var)]))


(def test-doc-data {:continuous-deployment-with-heroku  {:slug "continuous_deployment_with_heroku"
                                                         :sort_title "Continuous Deployment with Heroku"
                                                         :title "Continuous Deployment with Heroku"
                                                         :url "/docs/continuous-deployment-with-heroku"
                                                         :lastUpdated "May 7, 2013"
                                                         :markdown "## Quick start videos\n\nfoo\n\nbar\n..."}
                    :troubleshooting {:slug "troubleshooting"
                                      :sort_title "Troubleshooting"
                                      :title "Troubleshooting"
                                      :url "/docs/troubleshooting"
                                      :children [:troubleshooting-ruby]}
                    :troubleshooting-ruby {:slug "troubleshooting_ruby"
                                           :sort_title "Ruby"
                                           :title "Troubleshooting Ruby"
                                           :url "/docs/troubleshooting-ruby"
                                           :children [:bundler-latest :rspec-wrong-exit-code]}
                    :bundler-latest {:slug "bundler_latest"
                                     :sort_title "Do you need the latest version of Bundler?"
                                     :title "Do you need the latest version of Bundler?"
                                     :url "/docs/bundler-latest"
                                     :lastUpdated "Feb 3, 2013"}
                    :rspec-wrong-exit-code {:slug "rspec_wrong_exit_code"
                                            :sort_title "RSpec is failing but CircleCI reports my tests have passed"
                                            :title "RSpec is failing but CircleCI reports my tests have passed"
                                            :url "/docs/rspec-wrong-exit-code"
                                            :lastUpdated "Dec 20, 2013"}})

(def test-docs
  (let [updated (->> test-doc-data
                     vals
                     (map doc-utils/update-child-counts))]
    (->> (zipmap (keys test-doc-data) updated)
         doc-utils/update-children)))

(deftest test-subpage-rendering
  (let [test-doc (:continuous-deployment-with-heroku test-docs)
        test-node (goog.dom/htmlToDocumentFragment "<div class='content'></div>")]
    (om/root documentation/docs-subpage test-doc {:target test-node})
    (is (= "Continuous Deployment with Heroku"
           (-> test-node
               (sel1 :h1)
               dommy/text))
        "Title renders")
    (is (re-find #"Last Updated"
                 (-> test-node
                     (sel1 :p)
                     dommy/text))
        "Updated date renders")
    (is (= "Quick start videos"
           (-> test-node
               (sel1 :h2)
               dommy/text))
        "Content renders")
    (is (= "#quick-start-videos"
           (-> test-node
               (sel1 :a)
               (dommy/attr :href)))
        "Links are replaced")))

(deftest test-category-subpage-rendering
  (let [test-doc (:troubleshooting-ruby test-docs)
        om-component (om/build documentation/docs-subpage test-doc)
        rendered-component (test-utils/render-into-document om-component)]
    (is (= "Troubleshooting Ruby"
           (-> rendered-component
               om/get-node
               (sel1 :h1)
               dommy/text))
        "Title renders")
    (is (= "Do you need the latest version of Bundler?"
           (-> rendered-component
               om/get-node
               (sel1 :a)
               dommy/text))
        "List renders")))

(deftest test-categories-rendering
  (let [test-doc (:troubleshooting test-docs)
        om-component (om/build documentation/docs-categories [test-doc])
        rendered-component (test-utils/render-into-document om-component)]
    (is (re-find #"Troubleshooting"
           (-> rendered-component
               om/get-node
               (sel1 :h4)
               dommy/text)))
    (is (= "Ruby (2)"
           (-> rendered-component
               om/get-node
               (sel1 :li>a)
               dommy/text)))))

(deftest test-article-list-rendering
  (let [test-doc (:troubleshooting test-docs)
        om-component (om/build documentation/article-list (:children test-doc))
        rendered-component (test-utils/render-into-document om-component)
        test-node (om/get-node rendered-component)]
    (is (= "Troubleshooting Ruby (2)"
           (-> test-node
               (sel1 :a)
               dommy/text)))))

;; New markdown stuff
(deftest test-markdown
  (let [md->node #(-> (om/build documentation/markdown %)
                      test-utils/render-into-document
                      om/get-node)
        markdown-str "Hey There!\n==========\n"
        asset-str "Some text\n![](asset://img/docs/heroku-step1.png)"
        asset-path (stefon/asset-path "/img/docs/heroku-step1.png")
        git-version (aget js/CI "Versions" "git")
        ruby-versions (aget js/CI "Versions" "ruby_versions")
        version-str "Git version: {{ versions.git }}"
        versions-str "Ruby versions:\n {{versions.ruby_versions | code-list}}\n"
        api-str "Recent builds: \n {{ api_data.recent_builds | api-endpoint }}\n"]
    (is (= "Hey There!"  (-> markdown-str
                             md->node
                             (sel1 :h1)
                             dommy/text))
        "Markdown renders")
    (is (= asset-path (-> asset-str
                          md->node
                          (sel1 :img)
                          (dommy/attr :src)))
        "Asset paths are replaced")
    (is (re-find (js/RegExp. git-version) (-> version-str
                                              md->node
                                              dommy/text))
        "Versions render")
    (is (= (first ruby-versions) (-> versions-str
                                     md->node
                                     (sel1 :ul>li)
                                     dommy/text))
        "Version lists render")
    (is (= "curl https://circleci.com/api/v1/recent-builds?circle-token=:token&limit=20&offset=5"
           (-> api-str
               md->node
               (sel1 :pre)
               dommy/text))
        "API example renders")))

