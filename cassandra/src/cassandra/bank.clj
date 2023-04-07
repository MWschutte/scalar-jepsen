(ns cassandra.bank
  (:require [cassandra.core :refer :all]
            [cassandra.conductors :as conductors]
            [clojure.tools.logging :refer [debug info warn]]
            [clojure.core.reducers :as r]
            [jepsen
             [client :as client]
             [checker :as checker]
             [generator :as gen]
             ]
            [jepsen.tests.bank :as bank]
            [jepsen.checker.timeline :as timeline]
            [qbits.alia :as alia]
            [qbits.hayt.dsl.clause :refer :all]
            [qbits.hayt.dsl.statement :refer :all]
            [knossos.model :as model]
            [knossos.op :as op]
            )
  (:import (clojure.lang ExceptionInfo)))


;; counter batch https://docs.datastax.com/en/dse/6.0/cql/cql/cql_reference/cql_commands/cqlBatch.html#cqlBatch__batch-counter-updates

(defrecord BankSetClient [tbl-created? cluster session n starting-balance]
  client/Client
  (open! [_ test _]
    (let [cluster (alia/cluster {:contact-points (map name (:nodes test))})
          session (alia/connect cluster)]
      (->BankSetClient tbl-created? cluster session n starting-balance)))

  (setup! [_ test]
    (locking tbl-created?
      (when (compare-and-set! tbl-created? false true)
        (create-my-keyspace session test {:keyspace "jepsen_keyspace"})
        (create-my-table session {:keyspace "jepsen_keyspace"
                                  :table "bat"
                                  :schema {:pid         :int
                                            :id         :int
                                           :value        :counter
                                           :primary-key [:pid, :id]}})
                                           
        (Thread/sleep 100)
        (info "adding initial balance")
        (dotimes [i n]
          (Thread/sleep 500)
          (info "Creating account" i)
          ;; (alia/execute session (update :bat 
          ;;                         (values [[:pid 1] [:id i]])))
          (alia/execute session (update :bat
                                       (set-columns {:value [+ starting-balance]})
                                       (where [[= :pid 1] [= :id i]]))
                                       {:consistency  :all})
      ))))

  (invoke! [_ _ op]
    (try
      (alia/execute session (use-keyspace :jepsen_keyspace))
      (case (:f op)
        :transfer (let [value (:value op)]
                (let [to (:to value) from (:from value) amount (:amount value)]
                (warn (str "from " from " to " to" amount " amount))
               (alia/execute session
                              (str "BEGIN COUNTER BATCH "
                                  "UPDATE bat SET value = value + " amount " WHERE pid = 1 and id = " from "; "
                                  "UPDATE bat SET value = value - " amount " WHERE pid = 1 and id =" to ";"
                                  "APPLY BATCH;")
                             {:consistency :quorum})
               (assoc op :type :ok)))
        :read (->> (alia/execute session (select :bat) {:consistency :all})
                    (mapv :value)
                    (assoc op :type :ok, :value)))

      (catch ExceptionInfo e
        (handle-exception op e))))

  (close! [_ _]
    (close-cassandra cluster session))

  (teardown! [_ _]))

(defn bank-checker
  "Balances must sum to the model's total. 
  Nonneggative cannot be guaranteed since counters and ltw are not supported"
  [model]
  (reify checker/Checker
    (check [this test history opts]
      (let [bad-reads (->> history
                           (r/filter op/ok?)
                           (r/filter #(= :read (:f %)))
                           (r/map (fn [op]
                                    (let [balances (:value op)]
                                      (cond (not= (:n model) (count balances))
                                            {:type :wrong-n
                                             :expected (:n model)
                                             :found    (count balances)
                                             :op       op}

                                            (not= (:total model)
                                                  (reduce + balances))
                                            {:type :wrong-total
                                             :expected (:total model)
                                             :found    (reduce + balances)
                                             :op       op}
                                            ))))
                           (r/filter identity)
                           (into []))]
        {:valid? (empty? bad-reads)
         :bad-reads bad-reads}))))

(defn bank-test
  [opts]
  (merge (cassandra-test (str "bank-set-" (:suffix opts))
                         {:max-transfer  5
                          :total-amount  100
                          :accounts      (vec (range 8))
                          :client    (->BankSetClient (atom false) nil nil 8 10)
                          :checker   (checker/compose
                                    {:perf    (checker/perf)
                                      :timeline (timeline/html)
                                      :details (bank-checker {:total 80 :n 8})})
                          :generator (gen/phases
                                      (->> [(bank/generator)]
                                           (conductors/std-gen opts))
                                      (conductors/terminate-nemesis opts)
                                      ; read after waiting for batchlog replay
                                      (gen/sleep 60)
                                      (read-once))})
         opts))
