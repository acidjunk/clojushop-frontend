# Clojurescript shop frontend

Started with:
lein new reagent-frontend shop

Todo: find out if we need: +test +devcards +sass

This is a frontend for:
https://github.com/acidjunk/flask-shop


### Development mode

To start the Figwheel compiler, navigate to the project folder and run the following command in the terminal:

```
lein figwheel
```

Figwheel will automatically push cljs changes to the browser.
Once Figwheel starts up, you should be able to open the `public/index.html` page in the browser.


### Building for production

```
lein clean
lein package
```
