package io.kubeless;

import io.kubeless.Context;
import io.kubeless.Event;

public class PublicWithKubelessFunction5 {
    public String helloWorld(Event event, Context context) {
        return "Hello World";
    }
}