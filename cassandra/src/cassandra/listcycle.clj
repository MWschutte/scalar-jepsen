(ns cassandra.listset
  (:require [cassandra.core :refer :all]
            [elle.list-append :as la]
            [cassandra.conductors :as conductors]
            [clojure.tools.logging :refer [debug info warn]]
            [jepsen
             [client :as client]
             [checker :as checker]
             [generator :as gen]]
            [qbits.alia :as alia]
            [qbits.hayt.dsl.clause :refer :all]
            [qbits.hayt.dsl.statement :refer :all])
  (:import (clojure.lang ExceptionInfo)))


(defn checker
  "Full checker for append and read histories. See elle.list-append for
  options."
  ([]
   (checker {}))
  ([opts]
   (reify checker/Checker
     (check [this test history checker-opts]
       (la/check (assoc opts :directory
                        (.getCanonicalPath
                          (store/path! test (:subdirectory checker-opts) "elle")))
                 history)))))

(defn gen
  "Wrapper for elle.list-append/gen; as a Jepsen generator."
  [opts]
  (la/gen opts))

  
(defrecord ListSetClient [tbl-created? cluster session]
  ;; Checks strong consistency on the list datatype of Cassandra. 
  ;; For a set of :add operations and a final :read this tests checks that 
  ;; only sucesfull writes where executed
  client/Client
  (open! [_ test _]
    (let [cluster (alia/cluster {:contact-points (map name (:nodes test))})
          session (alia/connect cluster)]
      (->ListSetClient tbl-created? cluster session)))

  (setup! [_ test]
    (locking tbl-created?
      (when (compare-and-set! tbl-created? false true)
        (create-my-keyspace session test {:keyspace "jepsen_keyspace"})
        (create-my-table session {:keyspace "jepsen_keyspace"
                                  :table "list_table"
                                  :schema {:id         :int
                                           :data       :list<int>
                                           :primary-key [:id]}})
                                           (Thread/sleep 100)
        (info "adding initial list")
        (alia/execute session (str "INSERT INTO list_table (id, data) VALUES (0, []);"))
                                           )))

  (invoke! [_ _ op]
    (try
      (alia/execute session (use-keyspace :jepsen_keyspace))
      (case (:f op)
        :add (let [value (:value op)]
               (alia/execute session
                             (str "UPDATE list_table SET data = ["value"] + data "
                             "WHERE id = 0;")
                             {:consistency :quorum})
               (assoc op :type :ok))
         :read (->> (alia/execute session (select :list_table (where [[= :id 0]])) {:consistency :all})
                    (mapv :data)
                    first 
                    (into (sorted-set))
                    (assoc op :type :ok ,:value))
                  )

      (catch ExceptionInfo e
        (handle-exception op e))))

  (close! [_ _]
    (close-cassandra cluster session))

  (teardown! [_ _]))

(defn set-test
  [opts]
  (merge (cassandra-test (str "list-set-" (:suffix opts))
                         {:client    (->ListSetClient (atom false) nil nil)
                          :checker   (checker)
                          :generator  :generator (->>
                                        (gen/stagger 1/100 (gen))
                                        (gen/nemesis
                                        (cycle [(gen/sleep 5)
                                                {:type :info, :f :start}
                                                (gen/sleep 5)
                                                {:type :info, :f :stop}]))
                                        (gen/time-limit (:time-limit opts)))
                                      })
         opts))
