(ns resource-scheduler.core
  (:require [clojure.edn :as edn]
            [clojure.string :as string]
            [doric.core :refer [table]])
  (:import (clojure.lang PersistentQueue)
           (java.util Date))
  (:gen-class))

(defn map-vals
  "Returns m with f applied to its values."
  [f m]
  (into {} (for [[k v] m] [k (f v)])))

(defn queue
  ([] PersistentQueue/EMPTY)
  ([coll] (into (queue) coll)))

(defn parse [description]
  (edn/read-string (str "(" description ")")))

(defn parse-nodes [nodes-description]
  (->> nodes-description
       (parse)
       (map (partial zipmap [:id :resource-units]))
       (group-by :id)
       (map-vals (partial apply merge-with (comp (partial reduce +) hash-set)))
       (map-vals #(assoc % :jobs #{}))))

(defn parse-jobs [jobs-description]
  (->> jobs-description
       (parse)
       (map (partial zipmap [:required-resource-units :time-steps-required]))
       (queue)))

(defn resource-units-being-used
  [{:keys [resource-units jobs] :as node}]
  (reduce + (map :required-resource-units jobs)))

(defn remaining-resource-units
  [{:keys [resource-units jobs] :as node}]
  (- resource-units (resource-units-being-used node)))

(defn full-capacity?
  [{:keys [resource-units] :as node}]
  (<= resource-units (resource-units-being-used node)))

(defn sufficient-resource-units?
  [{:keys [required-resource-units] :as job}
   {:keys [resource-units] :as node}]
  (<= required-resource-units (remaining-resource-units node)))

(defn first-with-sufficient-resource-units [nodes job]
  (first (filter (partial sufficient-resource-units? job) (vals nodes))))

(defn start-job [scheduler {:keys [id] :as node} job]
  (println "[scheduler] - Starting job" job "on node" id
           (str "(remaining resource units: " (remaining-resource-units node) ")"))
  (-> scheduler
      (update-in [:jobs] pop)
      (update-in [:nodes id] update :jobs conj (assoc job
                                                      :time-steps-taken 0
                                                      :node-id id))))

(defn steps-to-go [{:keys [time-steps-taken time-steps-required] :as job}]
  (- time-steps-required time-steps-taken))

(defn job-finished? [job]
  (= 0 (steps-to-go job)))

(def job-running?
  (complement job-finished?))

(defn node-busy? [{:keys [resource-units jobs] :as node}]
  (not (empty? jobs)))

(defn scheduler-busy? [{:keys [nodes jobs] :as scheduler}]
  (or (not (empty? jobs))
      (some node-busy? (vals nodes))))

(defn job-step [job]
  (let [{:keys [time-steps-taken node-id] :as updated-job}
        (-> job
            (update :time-steps-taken inc))]
    (if (job-finished? updated-job)
      (println (str "[node-" node-id "]    -")
               "Job" (dissoc updated-job :time-steps-taken :node-id) "finished")
      (println (str "[node-" node-id "]    -")
               "Job" (dissoc updated-job :time-steps-taken :node-id)
               "has been running for" time-steps-taken "steps"
               (str "(" (steps-to-go updated-job) " to go)")))
    updated-job))

(defn node-step [{:keys [id jobs] :as node}]
  (-> node
      (update :jobs (partial map job-step))
      (update :jobs (partial filter job-running?))
      (update :jobs doall)))

(defn step-nodes [scheduler]
  (update scheduler :nodes (partial map-vals node-step)))

(defn scheduler-step [{:keys [nodes jobs] :as scheduler}]
  (if-let [{:keys [required-resource-units time-steps-required] :as job}
           (peek jobs)]
    (if-let [node (first-with-sufficient-resource-units nodes job)]
      (-> scheduler
          (step-nodes)
          (start-job node job))
      (do
        (println "[scheduler] - No resources to schedule" job "- waiting")
        (step-nodes scheduler)))
    (do
      (println "[scheduler] - Idle")
      (step-nodes scheduler))))

(defn -main
  [nodes-description jobs-description & args]
  (let [{:keys [jobs nodes] :as scheduler} {:nodes (parse-nodes nodes-description)
                                            :jobs (parse-jobs jobs-description)}]
    (if (seq jobs)
      (do
        (println "Job queue:" (count jobs))
        (println (table jobs)))
      (println "Job queue empty"))
    (if (seq nodes)
      (do
        (println "\nNodes:" (count nodes))
        (println (table [:id :resource-units {:name :jobs :when false}]
                        (vals nodes))))
      (println "\nNo nodes available"))
    (doseq [[i {:keys [nodes jobs]}] (->> (iterate scheduler-step scheduler)
                                          (take-while scheduler-busy?)
                                          (map vector (range)))]
      (println "\nStep" (inc i)))))
