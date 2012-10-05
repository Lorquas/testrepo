(ns com.redhat.qe.sm.gui.tests.system-tests
  (:use [test-clj.testng :only (gen-class-testng data-driven)]
        [com.redhat.qe.sm.gui.tasks.test-config :only (config
                                                       clientcmd)]
        [com.redhat.qe.verify :only (verify)]
        [slingshot.slingshot :only [throw+ try+]]
        gnome.ldtp)
  (:require [com.redhat.qe.sm.gui.tasks.tasks :as tasks]
            [clojure.tools.logging :as log]
             com.redhat.qe.sm.gui.tasks.ui)
  (:import [org.testng.annotations BeforeClass
                                   BeforeGroups
                                   Test
                                   DataProvider
                                   AfterClass]))

(def ldtpd-log "/var/log/ldtpd/ldtpd.log")

(defn ^{BeforeClass {:groups ["setup"]}}
  clear_env [_]
  (tasks/kill-app))

(defn ^{AfterClass {:groups ["setup"]
                    :alwaysRun true}}
  restart_env [_]
  (tasks/restart-app :unregister? true))

(defn ^{Test {:groups ["system"
                       "blockedByBug-656896"]}}
  check_libglade_warnings [_]
  (let [output (tasks/get-logging @clientcmd
                                  ldtpd-log
                                  "check_libglade_warnings"
                                  "libglade-WARNING"
                                  (tasks/start-app))]
    (verify (not (tasks/substring? "libglade-WARNING" output))))
  (tasks/kill-app))

(defn ^{Test {:groups ["system"
                       "blockedByBug-706384"]}}
  run_second_instance [_]
  (tasks/restart-app)
  (let [output (tasks/get-logging @clientcmd
                            ldtpd-log
                            "run_second_instance"
                            nil
                            (tasks/start-app)
                            (tasks/sleep 10000))]
    (verify (tasks/substring? "subscription-manager-gui is already running" output))
    (verify (not (tasks/substring? "Traceback" output)))))

(defn ^{Test {:groups ["system"
                       "blockedByBug-747014"]}}
  check_help_button [_]
  (try
    (tasks/restart-app)
    (tasks/ui click :getting-started)
    (tasks/sleep 3000)
    (verify (= 1 (tasks/ui guiexist :help-dialog)))
    (tasks/ui closewindow :help-dialog)
    (finally (tasks/restart-app))))

(defn check_escape_window [window shortcut]
  (tasks/restart-app :unregister? true)
  (let [exec-shortcut (fn [s] (tasks/ui generatekeyevent s))
        count-objects (fn [w] (count (tasks/ui getobjectlist w)))
        beforecount (do (exec-shortcut shortcut)
                        (count-objects window))
        fuckcache (fn [] (try+ (tasks/ui getchild "blah")
                              (catch Exception e "")))]
    (fuckcache)
    (log/info (str "Items: " beforecount))
    (fuckcache)
    (exec-shortcut "<ESC>")
    (exec-shortcut shortcut)
    (verify (= beforecount (count-objects window)))))

(data-driven
 check_escape_window {Test {:groups ["system"
                                     "blockedByBug-862099"]}}
 [[:register-dialog "<CTRL>r"]
  [:import-dialog "<CTRL>i"]])


;; TODO


(gen-class-testng)

