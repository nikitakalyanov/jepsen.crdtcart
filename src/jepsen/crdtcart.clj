(ns jepsen.crdtcart
  (:require [jepsen.cli :as cli]
            [jepsen.tests :as tests]))

(defn foo
  "I don't do a whole lot."
  [x]
  (println x "Hello, World!"))

(defn crdtcart-test
  "Construct a test map from command line options."
  [opts]
  (merge tests/noop-test
         {:pure-generators true}
         opts))

(defn -main
  "Handles command line arguments."
  [& args]
  (cli/run! (cli/single-test-cmd {:test-fn crdtcart-test})
            args))
