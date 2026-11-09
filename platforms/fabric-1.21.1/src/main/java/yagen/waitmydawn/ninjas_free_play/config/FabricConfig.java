package yagen.waitmydawn.ninjas_free_play.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class FabricConfig {
    private final List<Value<?>> values = new ArrayList<>();
    private Path path;
    private String section;
    private String comment;

    FabricConfig push(String section) {
        this.section = section;
        return this;
    }

    void pop() {
        section = null;
    }

    FabricConfig comment(String comment) {
        this.comment = comment;
        return this;
    }

    Value<Boolean> define(String key, boolean fallback) {
        return define(key, fallback, value -> value instanceof Boolean flag ? flag : null);
    }

    <T> Value<T> define(String key, T fallback, Function<Object, T> decode) {
        var value = new Value<>(section + "." + key, comment, fallback, decode);
        values.add(value);
        comment = null;
        return value;
    }

    Value<Double> defineInRange(String key, double fallback, double min, double max) {
        return defineNumber(key, fallback, min, max, false);
    }

    Value<Double> defineNumber(String key, double fallback, double min, double max, boolean integral) {
        Value<Double> entry = define(key, fallback, value -> {
            if (!(value instanceof Number number)) return null;
            double parsed = number.doubleValue();
            return Double.isFinite(parsed) && parsed >= min && parsed <= max && (!integral || parsed == Math.rint(parsed))
                    ? parsed : null;
        });
        entry.integral = integral;
        return entry;
    }

    Value<Integer> defineInRange(String key, int fallback, int min, int max) {
        return define(key, fallback, value -> {
            if (!(value instanceof Number number)) return null;
            int parsed = number.intValue();
            return number.doubleValue() == parsed && parsed >= min && parsed <= max ? parsed : null;
        });
    }

    void load(Path source) {
        try (var file = open(source)) {
            file.load();
            values.forEach(value -> value.read(file));
        }
        path = source;
    }

    void save() {
        if (path == null) throw new IllegalStateException("Config has not been loaded");
        try (var file = open(path)) {
            file.load();
            values.forEach(value -> value.write(file));
            file.save();
        }
    }

    private static CommentedFileConfig open(Path path) {
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create configuration directory for " + path, e);
        }
        return CommentedFileConfig.builder(path).sync().writingMode(WritingMode.REPLACE).build();
    }

    public static final class Value<T> {
        private final String key, comment;
        private final T fallback;
        private final Function<Object, T> decode;
        private boolean integral;
        private T value;

        private Value(String key, String comment, T fallback, Function<Object, T> decode) {
            this.key = key;
            this.comment = comment;
            this.fallback = fallback;
            this.decode = decode;
            value = fallback;
        }

        public T get() { return value; }

        public void set(T value) {
            T validated = decode.apply(value);
            if (validated == null) throw new IllegalArgumentException("Invalid value for " + key);
            this.value = validated;
        }

        private void read(CommentedFileConfig file) {
            T parsed = decode.apply(file.get(key));
            value = parsed == null ? fallback : parsed;
        }

        private void write(CommentedFileConfig file) {
            Object serialized = value;
            if (integral) serialized = ((Number) value).intValue();
            file.set(key, serialized);
            if (comment != null) file.setComment(key, comment);
        }
    }
}
