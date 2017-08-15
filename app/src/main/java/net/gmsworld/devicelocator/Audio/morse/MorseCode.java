package net.gmsworld.devicelocator.Audio.morse;

/**
 * Created by jstakun on 8/12/17.
 * from https://github.com/uschwar/morsecode/tree/master/MorseService/src/at/schwar/android/morse/service
 */

import java.util.HashMap;

public class MorseCode {

    private static HashMap<Character, String> codes = null;

    private static void initCodes(){
        codes.put('A', ".-");
        codes.put('B', "-...");
        codes.put('C', "-.-.");
        codes.put('D', "-..");
        codes.put('E', ".");
        codes.put('F', "..-.");
        codes.put('G', "--.");
        codes.put('H', "....");
        codes.put('I', "..");
        codes.put('J', ".---");
        codes.put('K', "-.-");
        codes.put('L', ".-..");
        codes.put('M', "--");
        codes.put('N', "-.");
        codes.put('O', "---");
        codes.put('P', ".--.");
        codes.put('Q', "--.-");
        codes.put('R', ".-.");
        codes.put('S', "...");
        codes.put('T', "-");
        codes.put('U', "..-");
        codes.put('V', "...-");
        codes.put('W', ".--");
        codes.put('X', "-..-");
        codes.put('Y', "-.--");
        codes.put('Z', "--..");
        codes.put('0', "-----");
        codes.put('1', ".----");
        codes.put('2', "..---");
        codes.put('3', "...--");
        codes.put('4', "....-");
        codes.put('5', ".....");
        codes.put('6', "-....");
        codes.put('7', "--...");
        codes.put('8', "---..");
        codes.put('9', "----.");
        codes.put('.', ".-.-.-");
        codes.put(',', "--..--");
        codes.put(':', "---...");
        codes.put(';', "-.-.-.");
        codes.put('?', "..--..");
        codes.put('-', "-....-");
        codes.put('_', "..--.-");
        codes.put('(', "-.--.");
        codes.put(')', "-.--.-");
        codes.put('=', "-...-");
        codes.put('+', ".-.-.");
        codes.put('/', "-..-.");
        codes.put('@', ".--.-.");
        codes.put(' ', " ");
    }

    private MorseCode() {
    }

    public static String getMorseCode(char key) {
        return getCodes().get(key);
    }

    public static HashMap<Character, String> getCodes() {
        if (null == codes) {
            codes = new HashMap<Character, String>();
            initCodes();
        }
        return codes;
    }

}
