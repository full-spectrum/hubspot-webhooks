(ns leads.plugins.hubspot.config)

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
    (->> {:port (or (first cli-args) (:PORT env))}
         (validate)
         (vreset! store))))
