package ac.uk.sussex.kn253.repository;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

/**
 * Represents a single model entry returned by the Ollama {@code /api/tags}
 * endpoint.
 */
@Entity
@Table(name = "ollama_model_info")
@JsonIgnoreProperties(ignoreUnknown = true)
public class OllamaModelInfo extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "model")
    private String model;

    @Column(name = "size", nullable = false)
    private long size;

    @Column(name = "digest")
    private String digest;

    @JsonProperty("modified_at")
    @Column(name = "modified_at")
    private Instant modifiedAt;

    @Column(name = "details_json", columnDefinition = "TEXT")
    @Convert(converter = DetailsConverter.class)
    private Details details;

    public OllamaModelInfo() {
    }

    /**
     * Returns the best runtime identifier for selecting this model.
     */
    public String runtimeName() {
        return name != null && !name.isBlank() ? name : model;
    }

    public String getName() {
        return name;
    }

    public String getModel() {
        return model;
    }

    public long getSize() {
        return size;
    }

    public String getDigest() {
        return digest;
    }

    public Instant getModifiedAt() {
        return modifiedAt;
    }

    public Details getDetails() {
        return details;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public void setModel(final String model) {
        this.model = model;
    }

    public void setSize(final long size) {
        this.size = size;
    }

    public void setDigest(final String digest) {
        this.digest = digest;
    }

    public void setModifiedAt(final Instant modifiedAt) {
        this.modifiedAt = modifiedAt;
    }

    public void setDetails(final Details details) {
        this.details = details;
    }

    // -------------------------------------------------------------------------
    // Nested types
    // -------------------------------------------------------------------------

    /**
     * Detailed metadata about a model's architecture and quantisation.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Details {

        private String family;
        private List<String> families;

        @JsonProperty("parameter_size")
        private String parameterSize;

        @JsonProperty("quantization_level")
        private String quantizationLevel;

        private String format;

        public Details() {
        }

        public Details(final String family,
                final List<String> families,
                final String parameterSize,
                final String quantizationLevel,
                final String format) {
            this.family = family;
            this.families = families;
            this.parameterSize = parameterSize;
            this.quantizationLevel = quantizationLevel;
            this.format = format;
        }

        public String getFamily() {
            return family;
        }

        public List<String> getFamilies() {
            return families;
        }

        public String getParameterSize() {
            return parameterSize;
        }

        public String getQuantizationLevel() {
            return quantizationLevel;
        }

        public String getFormat() {
            return format;
        }

        public void setFamily(final String family) {
            this.family = family;
        }

        public void setFamilies(final List<String> families) {
            this.families = families;
        }

        public void setParameterSize(final String parameterSize) {
            this.parameterSize = parameterSize;
        }

        public void setQuantizationLevel(final String quantizationLevel) {
            this.quantizationLevel = quantizationLevel;
        }

        public void setFormat(final String format) {
            this.format = format;
        }
    }

    @Converter
    public static class DetailsConverter implements jakarta.persistence.AttributeConverter<Details, String> {

        private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

        @Override
        public String convertToDatabaseColumn(final Details attribute) {
            if (attribute == null) {
                return null;
            }
            try {
                return MAPPER.writeValueAsString(attribute);
            } catch (final JsonProcessingException e) {
                throw new IllegalStateException("Unable to serialise Ollama model details", e);
            }
        }

        @Override
        public Details convertToEntityAttribute(final String dbData) {
            if (dbData == null || dbData.isBlank()) {
                return null;
            }
            try {
                return MAPPER.readValue(dbData, Details.class);
            } catch (final JsonProcessingException e) {
                throw new IllegalStateException("Unable to deserialise Ollama model details", e);
            }
        }
    }

}
