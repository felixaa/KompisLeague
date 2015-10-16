package no.aardal.kompisleague.models;

import java.io.Serializable;
import java.util.Map;

/**
 * Created by chrisaardal on 16/10/15.
 */
public class League implements Serializable {

    public String name;
    public String tier;
    public String queue;
    public Entries entries;


    public League build(Map map) {
        if (map != null) {
            this.name = (String)map.get("name");
            this.tier = (String)map.get("tier");
            this.queue = (String)map.get("queue");
            this.entries = new Entries().build((Map)map.get("entries"));

        }
        return this;
    }
}