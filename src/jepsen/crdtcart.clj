(ns jepsen.crdtcart
  (:require [jepsen.cli :as cli]
            [jepsen.client :as client]
            [jepsen.tests :as tests]
            [jepsen.generator :as gen]
            [jepsen.nemesis :as nemesis]
            [jepsen.checker :as checker]
            [clojure.core.reducers :as r]
            [jepsen.util :as util]
            [jepsen.checker.timeline :as timeline]
            [clojure.java.io :as io]
            [clojure.core :as c]
            [cheshire.core :as cheshire]
            [knossos.op :as op])
  (:import (java.net Socket)
           (java.io StringWriter)))

(def products [{"product_id" 1, "product_name" "test", "price" 1, "quantity" 1}
               {"product_id" 2, "product_name" "test 2", "price" 2, "quantity" 2}
               {"product_id" 3, "product_name" "test 3", "price" 3, "quantity" 1}
               {"product_id" 4, "product_name" "test 4", "price" 4, "quantity" 1}
               {"product_id" 5, "product_name" "test 5", "price" 5, "quantity" 2}])

(defn r   [_ _] {:type :invoke, :f :read, :value nil})
(defn add   [_ _] {:type :invoke, :f :add, :value (rand-nth products)})
(defn rm  [_ _] {:type :invoke, :f :remove, :value (rand-nth products)})

(defn foo
  "I don't do a whole lot."
  [x]
  (println x "Hello, World!"))

(defrecord Client [node]
  client/Client
  (open! [this test node]
    (assoc this :node node))

  (setup! [this test])

  (invoke! [this test op]
    (case (:f op)
      :read (assoc op :type :ok, :value (with-open [sock (Socket. node 9999)
                                                    writer (io/writer sock)
                                                    reader (io/reader sock)
                                                    response (StringWriter.)]
                                           (.append writer "show\n")
                                           (.flush writer)
                                           (io/copy reader response)
                                           (set (cheshire/parse-string (str response)))))
       :add (do (with-open [sock (Socket. node 9999)
                                                    writer (io/writer sock)
                                                    reader (io/reader sock)
                                                    response (StringWriter.)]
                                           (.append writer (str "add " (cheshire/generate-string (:value op)) "\n"))
                                           (.flush writer)
                                           (io/copy reader response)
                                           (str response))
                 (assoc op :type :ok))
       :remove (do (with-open [sock (Socket. node 9999)
                                                    writer (io/writer sock)
                                                    reader (io/reader sock)
                                                    response (StringWriter.)]
                                           (.append writer (str "remove " (cheshire/generate-string (:value op)) "\n"))
                                           (.flush writer)
                                           (io/copy reader response)
                                           (str response))
                 (assoc op :type :ok))))

  (teardown! [this test])

  (close! [_ test]
   ; we do not hold TCP connection opened, so nothing to close
   ))

(defn set-checker
  "Given a set of :add operations followed by a final :read, verifies that
  every successfully added element is present in the read, and that the read
  contains only elements for which an add was attempted. Based on jepsen.checker/set.
  Uses product_id as identifiers of set items, also tracks remove operations."
  []
  (reify checker/Checker
    (check [this test history opts]
      (let [attempts (->> history
                          (r/filter op/invoke?)
                          (r/filter #(= :add (:f %)))
                          (r/map :value)
                          (r/map (fn [cart-item] (get cart-item "product_id")))
                          (into #{}))
            adds (->> history
                      (r/filter op/ok?)
                      (r/filter #(= :add (:f %)))
                      (r/map :value)
                      (r/map (fn [cart-item] (get cart-item "product_id")))
                      (into #{}))
            rm-attempts (->> history
                          (r/filter op/invoke?)
                          (r/filter #(= :remove (:f %)))
                          (r/map :value)
                          (r/map (fn [cart-item] (get cart-item "product_id")))
                          (into #{}))
            removes (->> history
                      (r/filter op/ok?)
                      (r/filter #(= :remove (:f %)))
                      (r/map :value)
                      (r/map (fn [cart-item] (get cart-item "product_id")))
                      (into #{}))
            final-read (->> history
                            (r/filter op/ok?)
                            (r/filter #(= :read (:f %)))
                            (r/map :value)
                            (reduce (fn [_ x] x) nil))]
        (if-not final-read
          {:valid? :unknown
           :error  "Set was never read"}

        (let [final-read (map (fn [cart-item] (get cart-item "product_id")) (c/set final-read))]  
        (let [final-read (c/set final-read)
                
                expected-set (->> history
                            (r/filter op/invoke?)
                            (reduce (fn [acc x] (if (= (:f x) :add) (conj acc (get (:value x) "product_id")) (if (= (:f x) :remove) (disj acc (get (:value x) "product_id")) acc))) #{}))
                ; The OK set is every read value which we tried to add
                ok          (clojure.set/intersection final-read attempts)

                ; Unexpected records are those we *never* attempted.
                unexpected  (clojure.set/difference final-read attempts)

                ; Lost records are those we definitely added but weren't read -- either because they were deleted later, or truly lost
                lost        (clojure.set/difference adds final-read)

                ; Recovered records are those where we didn't know if the add
                ; succeeded or not, but we found them in the final set.
                recovered   (clojure.set/difference ok adds)
                
                ; The OK-removed is every lost value that were actually removed (or tried to remove)
                ok-removed (clojure.set/intersection lost rm-attempts)

                ; Truly lost are records that were not read and were not deleted
                truly-lost (clojure.set/difference lost ok-removed)

                ; Stale elements are records that we succesfully removed but they are still present in the read
                stale (clojure.set/intersection removes final-read)

                ; is-expected compares to expected state
                is-expected (= (c/set expected-set) final-read)]

            {:valid?              (and (empty? truly-lost) (empty? unexpected) is-expected)
             :attempt-count       (count attempts)
             :acknowledged-count  (count adds)
             :ok-count            (count ok)
             :lost-count          (count truly-lost)
             :recovered-count     (count recovered)
             :unexpected-count    (count unexpected)
             :ok-removed-count    (count ok-removed)
             :stale-count         (count stale)
             :is-expected         is-expected
             :expected            (c/set expected-set)
             :ok                  (util/integer-interval-set-str ok)
             :lost                (util/integer-interval-set-str truly-lost)
             :stale               (util/integer-interval-set-str stale)
             :ok-removed          (util/integer-interval-set-str ok-removed)
             :unexpected          (util/integer-interval-set-str unexpected)
             :recovered           (util/integer-interval-set-str recovered)})))))))


(defn crdtcart-test
  "Construct a test map from command line options."
  [opts]
  (merge tests/noop-test
         opts
         {:pure-generators true
          :client (Client. nil)
          :nemesis (nemesis/partition-random-halves)
          :checker (checker/compose
                    {:perf (checker/perf)
                     :set (set-checker)
                     :timeline (timeline/html)})
          :generator (gen/phases
                       (->> (gen/mix [r add rm])
                            (gen/stagger 1)
                            (gen/nemesis
                              (cycle [(gen/sleep 5)
                               {:type :info, :f :start}
                               (gen/sleep 5)
                               {:type :info, :f :stop}]))
                            (gen/time-limit 15))
                        (gen/log "stopping network problems")
                        (gen/nemesis (gen/once {:type :info, :f :stop}))
                        (gen/log "Waiting for syncronisation")
                        (gen/sleep 1)
                        (gen/clients (gen/once {:type :invoke, :f :read, :value nil})))}))

(defn -main
  "Handles command line arguments."
  [& args]
  (cli/run! (cli/single-test-cmd {:test-fn crdtcart-test})
            args))
