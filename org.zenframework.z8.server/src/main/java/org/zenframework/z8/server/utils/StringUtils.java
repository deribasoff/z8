package org.zenframework.z8.server.utils;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class StringUtils {
	public static boolean isEmpty(String str) {
		return str == null || str.length() == 0;
	}

	public static int indexOfAny(String str, int position, char[] searchChars) {
		if(isEmpty(str) || ArrayUtils.isEmpty(searchChars))
			return -1;

		for(int i = position; i < str.length(); i++) {
			char ch = str.charAt(i);
			for(int j = 0; j < searchChars.length; j++) {
				if(searchChars[j] == ch)
					return i;
			}
		}
		return -1;
	}

	public static int indexOfAny(String str, char[] searchChars) {
		return indexOfAny(str, 0, searchChars);
	}

	public static int indexOfAny(String str, String searchChars) {
		return indexOfAny(str, searchChars.toCharArray());
	}

	public static int indexOfAny(String str, int position, String searchChars) {
		return indexOfAny(str, position, searchChars.toCharArray());
	}

	static public String unescapeJava(String string) {
		Properties properties = new Properties();
		try {
			properties.load(new StringReader("key=" + string));
		} catch(IOException e) {
			return string;
		}
		return properties.getProperty("key");
	}

	public static String padLeft(String str, int size, char padChar) {
		int pads = size - str.length();
		return pads > 0 ? padding(pads, padChar).concat(str) : str;
	}

	public static String padRight(String str, int size, char padChar) {
		int pads = size - str.length();
		return pads > 0 ? str.concat(padding(pads, padChar)) : str;
	}

	public static List<String> asList(String str, String delimeter) {
		String[] array = str.split(delimeter);
		List<String> list = new ArrayList<String>(array.length);
		for(String s : array) {
			list.add(s.trim());
		}
		return list;
	}

	private static String padding(int repeat, char padChar) {
		final char[] buf = new char[repeat];

		for(int i = 0; i < buf.length; i++)
			buf[i] = padChar;

		return new String(buf);
	}

	static public byte[] charsToBytes(char[] chars) {
		if(chars == null)
			return null;

		byte[] bytes = new byte[chars.length * 2];

		for(int i = 0; i < chars.length; i++) {
			int ch = chars[i];
			bytes[i * 2] = (byte)(ch >>> 8);
			bytes[i * 2 + 1] = (byte)ch;
		}

		return bytes;
	}

	static public char[] bytesToChars(byte[] bytes) {
		if(bytes.length % 2 != 0)
			throw new RuntimeException("StringUtils.bytesToChars: wrong byte array length");

		char[] chars = new char[bytes.length / 2];

		for(int i = 0; i < bytes.length; i += 2) {
			int byte1 = bytes[i];
			int byte2 = bytes[i + 1];
			chars[i / 2] = (char)((byte1 << 8) + byte2);
		}

		return chars;
	}
}