(ns com.redhat.qe.sm.gui.tests.base
  (:use [test-clj.testng :only (gen-class-testng)]
	[com.redhat.qe.sm.gui.tasks.tasks])
  (:require [com.redhat.qe.sm.gui.tasks.test-config :as config])
  (:import [org.testng.annotations BeforeSuite AfterSuite]))

(defn ^{BeforeSuite {:groups ["setup"]}}
  startup [_]
  (config/init)
  (connect)
  (start-app))

(defn ^{AfterSuite {:groups ["setup"]}}
  killGUI [_]
  (.runCommand @config/clientcmd "killall -9 subscription-manager-gui"))

(gen-class-testng)