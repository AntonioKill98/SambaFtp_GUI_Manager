package org.antonio;

import java.util.ArrayList;

public class SmbCondBean {
    private String name;
    private ArrayList<String[]> properties;

    public SmbCondBean(String name) {
        this.name = name;
        this.properties = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public void addProperty(String key, String value) {
        addOrUpdate(properties, key, value);
    }

    public void modifyProperty(String key, String value) {
        addOrUpdate(properties, key, value);
    }

    public void removeProperty(String key) {
        properties.removeIf(pair -> pair[0].equalsIgnoreCase(key));
    }

    public ArrayList<String[]> getProperties() {
        return properties;
    }

    public String toFormattedString() {
        StringBuilder builder = new StringBuilder("[").append(name).append("]\n");
        for (String[] property : properties) {
            builder.append(property[0]).append(" = ").append(property[1]).append("\n");
        }
        return builder.toString();
    }

    private void addOrUpdate(ArrayList<String[]> properties, String key, String value) {
        for (String[] pair : properties) {
            if (pair[0].equalsIgnoreCase(key)) {
                pair[1] = value;
                return;
            }
        }
        properties.add(new String[]{key, value});
    }
}