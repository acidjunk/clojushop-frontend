(ns ^:figwheel-no-load shop.dev
  (:require
    [shop.core :as core]
    [devtools.core :as devtools]))


(enable-console-print!)

(devtools/install!)

(core/init!)
