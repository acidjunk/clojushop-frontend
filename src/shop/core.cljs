(ns shop.core
    (:require [json-html.core :refer [edn->hiccup]]
              [reagent.core :as reagent :refer [atom]]
              [secretary.core :as secretary :include-macros true]
              [accountant.core :as accountant]
              [goog.crypt.base64 :as b64]
              [ajax.core :refer [GET POST]]
              [reagent-forms.core :refer [bind-fields init-field value-of]]))

;; --------------------------------------------
;; State
(enable-console-print!)

(defn string-to-base64-string [original]
  (b64/encodeString original))

(defn say-hello! "Greets `name`, or the world if no name specified.
Try and call this function from the ClojureScript REPL."
  [& [name]]
  (print "Hello," (or name "World") "!"))

(defn search-items-by-name [search_phrase products]
  (vec ; return vector
    (filter identity ; filter out nil entries
      (into [] (map (fn [m] ; go thru the whole collection
       (if (= (:name m) search_phrase) (:id m))) ; add current id to results if name matches
        products))))) ; collection to use for searching


(defonce state
  (reagent/atom
    {:products      {}
     :product       ""
     :search-phrase ""
     :cart          []
     :customer      {}
     :user          {}
     :checkout      1}
   ))

;; Data
(defn load-products! "Fetches the list of products from the server and updates the state atom with it"
  [state]
  (GET "https://api.opnsense-hardware.nl/products"
           {:handler (fn [products] (swap! state assoc :products (#(zipmap (map :url %) %) products)) (println state))
            :error-handler (fn [details] (.warn js/console (str "Failed to refresh products from server: " details)))
            :response-format :json, :keywords? true})
  )

(defn load-customer! "Fetches the info of a customer by logging in with an registered e-mail and password"
  [state]
  (GET "https://api.opnsense-hardware.nl/customer/acidjunk@gmail.com"
       {:headers {:Authorization (str "Basic " (string-to-base64-string "acidjunk@gmail.com:acidjunk@gmail.com"))}
        :handler (fn [customer] (swap! state assoc :customer customer))
        :error-handler (fn [details] (.warn js/console (str "Failed to fetch customers from server: " details)))
        :response-format :json, :keywords? true}))

(defn reset-customer! "Resets the state of the user and customer so the user will be logged out and customer data empty."
  [state]
  (swap! state assoc :customer {})
  (swap! state assoc :user {})
  (swap! state assoc :checkout 1))

(defn add-to-cart! "Add's an empty to the cart"
  [state, product]
  (let [{:keys [cart]} @state]
  (swap! state assoc :cart (conj cart product))))

(defn reset-cart! "Resets the state of the cart"
  [state]
  (let [{:keys [cart]} @state]
    (swap! state assoc :cart (drop-last cart))))

(defn checkout-step!
  [state, step]
  (let [{:keys [checkout]} @state]
    (swap! state assoc :checkout step)))

;; -------------------------
;; Components
(defn menu
  [item]
  (let [{:keys [cart]} @state]
  [:div {:class "ui inverted segment"}
   [:div {:class "ui inverted secondary pointing menu"}
    [:div {:class (if(= "products" item) "item active" "item")} [:a {:href "/"} "Products"]]
    [:div {:class (if(= "about" item) "item active" "item")} [:a {:href "/about"} "About"]]

    ;; Temporary
    ; [:div {:class (if(= "checkout" item) "item active" "item")} [:a {:href "/checkout"} "Checkout"]]


    [:div {:class "right menu"}
     [:div {:class (if(= "cart" item) "item active" "item")}
      [:a {:href "/cart"} [:i {:class "shop icon"}
                           [:div {:class "floating ui red circular tiny label"} (count cart)]]]]
     [:div {:class (if(= "contact" item) "item active" "item")} [:a {:href "/contact"} [:i {:class "mail icon"}]]]

;     ([:div {:class "item"}  [:i {:class "user icon"}] "yo yo"] (fnil ) )

     ;; login
     [:div {:class (if(= "login" item) "item active" "item")} [:a {:href "/login"} [:i {:class "user icon"}]]]
     ;;  Force auto login for now :)
     ; [:div {:class "item" :on-click #(load-customer! state)} [:i {:class "user icon"}] ""]
     ; [:div {:class "item" :on-click #(reset-customer! state)} [:i {:class "close icon"}] ""]
    ]
   ]]))

(defn timer-component []
  (let [seconds-elapsed (reagent/atom 0)]     ;; setup, and local state
    (fn []        ;; inner, render function is returned
      (js/setTimeout #(swap! seconds-elapsed inc) 1000)
      [:div "Seconds Elapsed: " @seconds-elapsed])))

(defn product-item [{:keys [name seo_name url intro list_image usp1 usp2 usp3 categories price]}]
  [:div {:class "card"}
   [:div {:class "image"} [:img {:src list_image}]]
   [:div {:class "content"}
    [:div {:class "header"} name]
    [:div {:class "meta"}
     (for [category (->> categories)]
      ^{:key (:name category)} [:div {:class "ui tiny label"} category]
     )
    ]
    [:div {:class "description"} intro
     [:ul [:li usp1] [:li usp2] [:li usp3]]
     [:div "€" + price]
    ]

   ]
   [:div {:class "extra content"}
    [:span {:class "right floated"} [:button {:class "ui orange labeled icon button" :on-click #(add-to-cart! state name)} [:i {:class "plus icon"}] "cart"]]
    [:span [:a {:href (str "/product/" url)} [:button {:class "ui orange labeled icon button" } [:i {:class "search icon"}] "details"]]]
   ]

   ])


(defn product-list [product-list]
  (let [{:keys [search-phrase]} @state]
  [:div {:class "ui link cards"}
    (if (clojure.string/blank? search-phrase)
      (for [product product-list]
        ^{:key product} [product-item (second product)]) ;; Show all products
      (for [product product-list]
        (if(not= -1 (.indexOf (first product) search-phrase)) ;; Fall back to javascript search
          ^{:key product} [product-item (second product)]
        )
       )
     )
   ;;     (doseq [keyval product-list] [product-item (val keyval)])
   ]))

(defn labeled-field [label input]
  [:div {:class "field"}
   [:label label]
   [:div input]])

(defn field [input]
  [:div {:class "field"}
   [:div input]])

(defn icon-field [icon input]
  [:div {:class "ui large icon input"}
   [:div input]
    [:i {:class "search link icon"}]])

(def search-form
  [:div {:class "ui form"} (icon-field "search link" [:input {:field :text :id :search-phrase}])])


;; -------------------------
;; Views


(defn home-page []
  (let [{:keys [products search-phrase]} @state] ;; funny -> keys are not needed for bind-fields?
    [:div {:class "ui container"}
     (menu "products")
     [:h1 {:class "ui header"} "Products"]

     [bind-fields search-form state]
     [:div {:class "ui hidden divider"}]

     [:div [product-list products]]
     ;;[:div {:class "ui segment"} [:div {:class "content"} [edn->hiccup @state]]]
   ]))

(defn product-page [name]
  (let [{:keys [product products search-phrase]} @state] ;; funny -> keys are not needed for bind-fields?
    [:div {:class "ui container"}
     (menu "products")
     [:a {:href "/"} "<< back"]
     [:h1 {:class "ui header"} product]
     [:div {:class "ui image"} [:img {:src (get-in products [product :detail_image])}]]
     [:div
       (for [category (->> (get-in products [product :categories]))]
         ^{:key (:name category)} [:div {:class "ui label"} category]
       )
     ]
     [:div {:class "ui hidden divider"}]
     [:div [:h3 "Description"] (get-in products [product :content])]
     [:div [:h3 "Price"] (str "€ " (get-in products [product :price]))]
     [:div {:class "ui hidden divider"}]
     [:span {:class "right floated"} [:button {:class "ui orange labeled icon button" :on-click #(add-to-cart! state product)} [:i {:class "plus icon"}] "add to cart"]]
   ]))


(defn about-page []
  [:div {:class "ui container"}
   (menu "about")
   [:h1 {:class "ui header"} "About us"]
   [:h2 {:class "ui header"} "Security made easy"]
   [:p "Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas. Vestibulum tortor quam, feugiat vitae, ultricies eget, tempor sit amet, ante. Donec eu libero sit amet quam egestas semper. Aenean ultricies mi vitae est. Mauris placerat eleifend leo. Quisque sit amet est et sapien ullamcorper pharetra. Vestibulum erat wisi, condimentum sed, commodo vitae, ornare sit amet, wisi. Aenean fermentum, elit eget tincidunt condimentum, eros ipsum rutrum orci, sagittis tempus lacus enim ac dui. Donec non enim in turpis pulvinar facilisis. Ut felis. Praesent dapibus, neque id cursus faucibus, tortor neque egestas augue, eu vulputate magna eros eu erat. Aliquam erat volutpat. Nam dui mi, tincidunt quis, accumsan porttitor, facilisis luctus, metus"]
   [:img {:class "ui fluid image" :src "https://data.kommago.nl/img/bnrs/frontpage/585159a6d6008.jpg"}]
   [:p "Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas. Vestibulum tortor quam, feugiat vitae, ultricies eget, tempor sit amet, ante. Donec eu libero sit amet quam egestas semper. Aenean ultricies mi vitae est. Mauris placerat eleifend leo. Quisque sit amet est et sapien ullamcorper pharetra. Vestibulum erat wisi, condimentum sed, commodo vitae, ornare sit amet, wisi. Aenean fermentum, elit eget tincidunt condimentum, eros ipsum rutrum orci, sagittis tempus lacus enim ac dui. Donec non enim in turpis pulvinar facilisis. Ut felis. Praesent dapibus, neque id cursus faucibus, tortor neque egestas augue, eu vulputate magna eros eu erat. Aliquam erat volutpat. Nam dui mi, tincidunt quis, accumsan porttitor, facilisis luctus, metus"]
  ])


(def checkout-form-1
  [:form {:class "ui form"}
  [:h2 {:class "ui header"} "Shipping address"]

  [:div {:class "required field"} [:label "Name"]
   [:div {:class "two fields"}
    [:div {:class "field"} [:input {:name "first-name" :placeholder "First Name" :field :text :id :customer.first_name}]]
    [:div {:class "field"} [:input {:name "last-name" :placeholder "Last Name" :field :text :id :customer.last_name}]]
    ]
   ]
   [:div {:class "field"} [:label "Company"] [:input {:name "company" :placeholder "Company" :field :text :id :customer.company_name}]]
   [:div {:class "field"} [:label "VAT number"] [:input {:name "company" :placeholder "VAT number" :field :text :id :customer.vat_number}]]


  [:div {:class "required field"} [:label "Address"] [:input {:name "address" :placeholder "Address" :field :text :id :customer.street}]]

  [:div {:class "two fields"}
   [:div {:class "required field"} [:label "Postal code"] [:input {:name "postal-code" :placeholder "Postal code" :field :text :id :customer.zip_code}]]
   [:div {:class "required field"} [:label "City"][:input {:name "city" :placeholder "City" :field :text :id :customer.city}]]
   ]
  [:div {:class "required field"} [:label "Country"] [:input {:name "address" :placeholder "Address" :field :text :id :customer.country}]]
   [:div {:class "ui large submit button" :on-click #(checkout-step! state 2)} "Next Step"]

  ])


(def checkout-form-2
  [:form {:class "ui form"}
   [:h2 {:class "ui header"} "Billing address"]
   [:div {:class "field"} [:label "Use shipping address as billing address"] [:div {:class "ui toggle checkbox"} [:input {:type "checkbox"}]  ]  ]
   [:div {:class "ui large submit button" :on-click #(checkout-step! state 3)} "Next Step"]
  ])

(def checkout-form-3
  [:form {:class "ui form"}
   [:div "STEP 3"]
   [:div {:class "ui large submit button" :on-click #(checkout-step! state 1)} "Finish"]
  ])

(defn checkout-page []
  (let [{:keys [cart customer checkout]} @state]
  [:div {:class "ui container"}
   (menu "checkout")
   [:h1 {:class "ui header"} "Checkout"]

   [:div {:class "ui tablet stackable steps"}

    [:div {:class (if (= checkout 1) "active step" "step")} [:i {:class "truck icon"}]
     [:div {:class "content"}
      [:div {:class "title"} "Shipping"]
      [:div {:class "description"} "Enter shipping address"]
    ]]

    [:div {:class (if (= checkout 2) "active step" "step")} [:i {:class "newspaper icon"}]
     [:div {:class "content"}
      [:div {:class "title"} "Billing"]
      [:div {:class "description"} "Enter billing address"]
    ]]

    [:div {:class (if (= checkout 3) "active step" "step")} [:i {:class "money bill icon"}]
     [:div {:class "content"}
      [:div {:class "title"} "Payment"]
      [:div {:class "description"} "Enter billing information"]
    ]]

   ]  ;; end stackable steps


   ;; Form 1

   [:div {:class "ui blue segment"}
    (if (= checkout 1) [bind-fields checkout-form-1 state])
    (if (= checkout 2) [bind-fields checkout-form-2 state])
    (if (= checkout 3) [bind-fields checkout-form-3 state])

    (println checkout)
   ] ;; end ui form

   ]))

(defn cart_item
  [product]
  [:div {:class "ui segment"} product]

  )

(defn cart-page []
  (let [{:keys [cart customer]} @state]
    [:div {:class "ui container"}
     (menu "cart")
     [:h1 {:class "ui header"} "Your shopping basket"]
     [:div {:class "ui button" :on-click #(reset-cart! state)} "reset"]
     [:div {:class "ui hidden divider"}]

     [:div {:class "ui list"}
       (for [product (->> cart)]
         ^{:key (:name product)} [:div {:class "item"} (cart_item product)] )
     ]
     [:a {:href "/checkout"} [:button {:class "ui animated fade button"} [:div {:class "visible content"} "Checkout"] [:div {:class "hidden content"} "Now"]]]
    ]
  ))

(defn send-contact []
 (println "iue")
)

(def contact-form
  [:form {:class "ui form"}

   [:div {:class "required field"} [:label "Name"]
    [:div {:class "two fields"}
     [:div {:class "field"} [:input {:name "first-name" :placeholder "First Name" :field :text :id :customer.first_name}]]
     [:div {:class "field"} [:input {:name "last-name" :placeholder "Last Name" :field :text :id :customer.last_name}]]
     ]
    ]

   [:div {:class "field"} [:label "Company"] [:input {:name "company" :placeholder "Company" :field :text :id :customer.company_name}]]
   [:div {:class "required field"} [:label "E-mail"] [:input {:name "email" :placeholder "E-mail" :field :email :id :user.email}]]
   [:div {:class "field"} [:label "Product"] [:input {:name "product" :placeholder "Product"}]]
   [:div {:class "required field"} [:label "Question/remark"] [:textarea]]
   [:div {:class "ui large submit button" :on-click #(send-contact)} "Send"]

   ])

(defn contact-page []
  [:div {:class "ui container"}
   (menu "contact")
   [:h1 {:class "ui header"} "Contact us"]
   [bind-fields contact-form state]
  ])


(def login-form
  [:form {:class "ui form"}

   [:div {:class "field"}
    [:div {:class "ui left icon input"} [:i {:class "user icon"}] [:input {:name "email" :placeholder "E-mail" :field :email :id :user.email}]]]

   [:div {:class "field"}
    [:div {:class "ui left icon input"} [:i {:class "lock icon"}] [:input {:name "password" :type "password" :placeholder "Password" :field :text :id :user.password}]]]

   [:div {:class "ui large submit button" :on-click #(load-customer! state)} "Login"]
   ]
)


(defn login-page []
  (let [{:keys [user]} @state]
  [:div {:class "ui container"}
   (menu "login")
   [:h1 {:class "ui header"} "Login"]


   [:div {:class "ui blue segment"}
    [bind-fields login-form state]


   ] ;; end login segment and form


   ; [:div {:class "ui segment"}  [:div {:class "content"} [edn->hiccup @state]] ]



   ]))

;; -------------------------
;; Routes

(defonce page (atom #'home-page))

(defn current-page []
  [:div [@page]])

(secretary/defroute "/" []
  (reset! page #'home-page))

(secretary/defroute "/product/:url" [url]
 (let [{:keys [product, products]} @state]
 (swap! state assoc :product url)
 (reset! page #'product-page)))

(secretary/defroute "/about" []
  (reset! page #'about-page))

(secretary/defroute "/checkout" []
  (reset! page #'checkout-page))

(secretary/defroute "/cart" []
  (reset! page #'cart-page))

(secretary/defroute "/contact" []
  (reset! page #'contact-page))

(secretary/defroute "/login" []
  (reset! page #'login-page))

;; -------------------------
;; Initialize app

(defn mount-root []
  (reagent/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (accountant/configure-navigation!
    {:nav-handler
     (fn [path]
       (secretary/dispatch! path))
     :path-exists?
     (fn [path]
       (secretary/locate-route path))})
  (accountant/dispatch-current!)
  (load-products! state)
  (mount-root))
