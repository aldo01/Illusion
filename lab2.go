package main 
import (
"fmt"
"github.com/julienschmidt/httprouter"
"net/http"
"encoding/json"
"log")
type test_struct struct {
	Name string 
}
type response_struct struct {
	Greeting string
}
func postcreate(w http.ResponseWriter, r *http.Request, rh httprouter.Params) {
	m:= new(test_struct)
	n:= new(response_struct)
	decoder:= json.NewDecoder(r.Body)
    err:= decoder.Decode(&m)
    if err!=nil {
    	log.Println(err.Error())
    	http.Error(w, err.Error(), http.StatusInternalServerError)
    	return
    }
    n.Greeting= "hello, "+ m.Name + "!"
    output, err:= json.Marshal(n)
    if err!=nil {
    	log.Println(err.Error())
    	http.Error(w, err.Error(), http.StatusInternalServerError)
    	return
    }
    w.Header().Set("Content-Type", "application/json")
    w.WriteHeader(http.StatusCreated)
	fmt.Fprintf(w, string(output))
}
func Hello(w http.ResponseWriter, r *http.Request, ps httprouter.Params) {
fmt.Fprintf(w, "hello, %s!\n", ps.ByName("name"))	
}
func main() {
	router := httprouter.New()
	router.GET("/hello/:name", Hello)
	router.POST("/hello", postcreate)
	log.Fatal(http.ListenAndServe(":7171", router))
}