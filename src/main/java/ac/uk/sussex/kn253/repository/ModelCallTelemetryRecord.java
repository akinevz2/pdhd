package ac.uk.sussex.kn253.repository;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

/**
 * Persistent record of one assistant model call.
 */
@Entity
@Table(name = "model_call_telemetry")
public class ModelCallTelemetryRecord extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "model_name", nullable = false)
    public String modelName;

    @Column(name = "current_working_directory")
    public String currentWorkingDirectory;

    @Column(name = "user_message", columnDefinition = "TEXT", nullable = false)
    public String userMessage;

    @Column(name = "model_response", columnDefinition = "TEXT")
    public String modelResponse;

    @Column(name = "request_token_count", nullable = false)
    public int requestTokenCount;

    @Column(name = "response_token_count", nullable = false)
    public int responseTokenCount;

    @Column(name = "duration_nanos", nullable = false)
    public long durationNanos;

    @Column(name = "error_class")
    public String errorClass;

    @Column(name = "success", nullable = false)
    public boolean success;

    @Column(name = "recorded_at", nullable = false)
    public long recordedAt;
}