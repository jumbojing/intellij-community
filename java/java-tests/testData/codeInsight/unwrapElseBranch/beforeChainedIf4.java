// "Unwrap 'else' branch" "true"

class T {
    String f(boolean a, boolean b) {
        if (b) {
            return "When true";
        } else if (a) {
            return "a";
        } <caret>else {
            return "Otherwise";
        }
        return "Default";
    }
}