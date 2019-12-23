package io.kubeless;

private class PrivateAccessModifier {
    public String helloWorld(Event event, Context context) {
        return "Hello World";
    }
}