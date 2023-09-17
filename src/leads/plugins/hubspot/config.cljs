(ns leads.plugins.hubspot.config
  (:require ["fs" :as fs]))

(defonce store
  (volatile! nil))

(defn jsx->clj
  [x]
  (into {} (for [k (.keys js/Object x)]
             [(keyword k) (aget x k)])))

(defn validate
  [conf]
  (when-not (:port conf)
    (throw (ex-info (str "Webserver port configuration missing") {})))
  conf)

(defn setup!
  [cli-args]
  (let [env (jsx->clj (.-env js/process))]
    (->> {:port (or (first cli-args) (:PORT env))
          :webhook-url (:LEADS_HUBSPOT_PLUGIN_WEBHOOK_URL env)
          :secret (when-let [secret-path (or (second cli-args)
                                             (:LEADS_HUBSPOT_PLUGIN_APP_SECRET_PATH env))]
                    (.readFileSync fs secret-path "utf8"))}
         (validate)
         (vreset! store))))
