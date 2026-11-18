package com.company.promoengine.support;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.TreeSet;

public final class JsonComparison {

    private JsonComparison() {
    }

    public static void assertEquals(JsonNode expected, JsonNode actual) {
        compare(expected, actual, "$");
    }

    private static void compare(JsonNode expected, JsonNode actual, String path) {
        if (expected == null || expected.isMissingNode()) {
            throw new AssertionError(path + " is missing from the fixture");
        }
        if (actual == null || actual.isMissingNode()) {
            throw new AssertionError(path + " is missing from the response");
        }
        if (expected.isNumber() || actual.isNumber()) {
            if (!expected.isNumber() || !actual.isNumber()) {
                fail(path, expected, actual);
            }
            if (expected.decimalValue().compareTo(actual.decimalValue()) != 0) {
                fail(path, expected.decimalValue().toPlainString(), actual.decimalValue().toPlainString());
            }
            return;
        }
        if (expected.getNodeType() != actual.getNodeType()) {
            fail(path, expected, actual);
        }
        switch (expected.getNodeType()) {
            case OBJECT -> compareObject(expected, actual, path);
            case ARRAY -> compareArray(expected, actual, path);
            case NULL -> {
                // both null; node types already match
            }
            default -> {
                if (!expected.equals(actual)) {
                    fail(path, expected, actual);
                }
            }
        }
    }

    private static void compareObject(JsonNode expected, JsonNode actual, String path) {
        var expectedNames = names(expected);
        var actualNames = names(actual);
        if (!expectedNames.equals(actualNames)) {
            var missing = new TreeSet<>(expectedNames);
            missing.removeAll(actualNames);
            var unexpected = new TreeSet<>(actualNames);
            unexpected.removeAll(expectedNames);
            throw new AssertionError(path + " fields differ. missing=" + missing + " unexpected=" + unexpected);
        }
        for (String name : expectedNames) {
            compare(expected.get(name), actual.get(name), path + "." + name);
        }
    }

    private static void compareArray(JsonNode expected, JsonNode actual, String path) {
        if (expected.size() != actual.size()) {
            throw new AssertionError(path + " length expected " + expected.size() + " but was " + actual.size());
        }
        for (int i = 0; i < expected.size(); i++) {
            compare(expected.get(i), actual.get(i), path + "[" + i + "]");
        }
    }

    private static TreeSet<String> names(JsonNode node) {
        var names = new TreeSet<String>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static void fail(String path, Object expected, Object actual) {
        throw new AssertionError(path + "\nexpected: " + expected + "\nactual:   " + actual);
    }
}
