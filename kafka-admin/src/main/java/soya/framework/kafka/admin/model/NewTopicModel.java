package soya.framework.kafka.admin.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.kafka.clients.admin.NewTopic;

import java.util.LinkedHashSet;
import java.util.Set;

public class NewTopicModel {
    private String name;
    private int partitions = 1;
    private short replications = 1;

    public NewTopicModel setName(String name) {
        this.name = name;
        return this;
    }

    public NewTopicModel setPartitions(int partitions) {
        if (partitions > 1) {
            this.partitions = partitions;
        }
        return this;

    }

    public NewTopicModel setReplications(short replications) {
        if (replications > 1) {
            this.replications = replications;
        }
        return this;
    }

    public static NewTopicModel newInstance() {
        return new NewTopicModel();
    }

    public static Set<NewTopic> fromJson(String json) {
        Set<NewTopic> set = new LinkedHashSet<>();
        JsonElement jsonElement = JsonParser.parseString(json);
        if (jsonElement.isJsonPrimitive()) {
            NewTopicModel model = new NewTopicModel();
            model.setName(jsonElement.getAsString());
            set.add(model.build());

        } else if (jsonElement.isJsonObject()) {
            set.add(fromJsonObject(jsonElement.getAsJsonObject()).build());

        } else if(jsonElement.isJsonArray()) {
            jsonElement.getAsJsonArray().forEach(e -> {
                set.add(fromJsonObject(e.getAsJsonObject()).build());
            });
        }

        return set;
    }

    private static NewTopicModel fromJsonObject(JsonObject jsonObject) {
        NewTopicModel model = new NewTopicModel();
        if (jsonObject.get("name") != null) {
            model.setName(jsonObject.get("name").getAsString());
        }

        if (jsonObject.get("partitions") != null) {
            model.setPartitions(jsonObject.get("partitions").getAsInt());
        }

        if(jsonObject.get("replications") != null) {
            model.setReplications(jsonObject.get("replications").getAsShort());
        }

        return model;
    }

    public NewTopic build() {
        return new NewTopic(name, partitions, replications);
    }
}
