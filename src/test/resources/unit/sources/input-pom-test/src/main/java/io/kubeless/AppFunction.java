package io.kubeless;

public class AppFunction {
    public String helloWorld(Event event, Context context) {
        return "Hello World";
    }
}