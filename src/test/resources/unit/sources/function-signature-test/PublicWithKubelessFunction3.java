package io.kubeless;

import io.kubeless.Event;

public class PublicWithKubelessFunction3 {
    public String helloWorld(Event event, Context context) {
        return "Hello World";
    }
}