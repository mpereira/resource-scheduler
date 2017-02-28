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
       (map (partial zipmap [:required-resource-units :time-steps-required]))))

(defn resource-units-being-used
  [{:keys [resource-units jobs] :as node}]
  (reduce + (map :required-resource-units jobs)))

(defn remaining-resource-units
  [{:keys [resource-units jobs] :as node}]
  (- resource-units (resource-units-being-used node)))

(defn sufficient-resource-units?
  [{:keys [required-resource-units] :as job}
   {:keys [resource-units] :as node}]
  (<= required-resource-units (remaining-resource-units node)))

(defn first-with-sufficient-resource-units [nodes job]
  (first (filter (partial sufficient-resource-units? job) (vals nodes))))

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

(defprotocol Scheduler
  (step [this] "Advances the scheduler 1 step in time")
  (start-job [this node job] "Starts job in node"))

(defrecord FirstComeFirstServedScheduler []
  Scheduler
  (step [{:keys [nodes jobs] :as this}]
    (if-let [{:keys [required-resource-units time-steps-required] :as job}
             (peek jobs)]
      (if-let [node (first-with-sufficient-resource-units nodes job)]
        (-> this
            (step-nodes)
            (start-job node job))
        (do
          (println "[scheduler] - No resources to schedule" job "- waiting")
          (step-nodes this)))
      (do
        (println "[scheduler] - Idle")
        (step-nodes this))))

  (start-job [this {:keys [id] :as node} job]
    (println "[scheduler] - Starting job" job "on node" id
             (str "(remaining resource units: " (remaining-resource-units node) ")"))
    (-> this
        (update-in [:jobs] pop)
        (update-in [:nodes id] update :jobs conj (assoc job
                                                        :time-steps-taken 0
                                                        :node-id id)))))

(defn first-job-and-node-with-sufficient-resource-units
  [{:keys [nodes jobs] :as scheduler}]
  (first (for [job jobs
               node (vals nodes)
               :when (sufficient-resource-units? job node)]
           [job node])))

(defrecord SmartScheduler []
  Scheduler
  (step [{:keys [nodes jobs] :as this}]
    (if (seq jobs)
      (if-let [[{:keys [required-resource-units time-steps-required] :as job}
                node]
               (first-job-and-node-with-sufficient-resource-units this)]
        (-> this
            (step-nodes)
            (start-job node job))
        (do
          (println "[scheduler] - No resources to schedule jobs - waiting")
          (step-nodes this)))
      (do
        (println "[scheduler] - Idle")
        (step-nodes this))))

  (start-job [this {:keys [id] :as node} job]
    (println "[scheduler] - Starting job" job "on node" id
             (str "(remaining resource units: " (remaining-resource-units node) ")"))
    (-> this
        (update-in [:jobs] disj job)
        (update-in [:nodes id] update :jobs conj (assoc job
                                                        :time-steps-taken 0
                                                        :node-id id)))))

(defn make-first-come-first-served-scheduler [{:keys [jobs nodes]}]
  (map->FirstComeFirstServedScheduler {:nodes nodes
                                       :jobs (queue jobs)}))

(defn make-smart-scheduler [{:keys [jobs nodes]}]
  (map->SmartScheduler {:nodes nodes
                        :jobs (set jobs)}))

(def scheduler-types #{"first-come-first-served" "smart"})

(defn -main
  [scheduler-type nodes-description jobs-description & args]
  (if (contains? scheduler-types scheduler-type)
    (let [make-scheduler-fn
          (get {"first-come-first-served" make-first-come-first-served-scheduler
                "smart" make-smart-scheduler}
               scheduler-type)
          {:keys [jobs nodes] :as scheduler}
          (make-scheduler-fn {:nodes (parse-nodes nodes-description)
                              :jobs (parse-jobs jobs-description)})]
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
      (doseq [[i {:keys [nodes jobs]}] (->> (iterate step scheduler)
                                            (take-while scheduler-busy?)
                                            (map vector (range)))]
        (println "\nStep" (inc i))))
    (do
      (println "Invalid scheduler type:" scheduler-type)
      (println "Must be one of" scheduler-types))))

(comment
  (-main "smart"
         "(2 3) (7 1) (1 10) (8 5) (2 8)"
         "(3 4) (1 4) (4 7) (1 3) (5 4) (9 3)"))
