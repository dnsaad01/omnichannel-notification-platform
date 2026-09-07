package com.eventflow.ingestion.simulator;

import com.eventflow.ingestion.dto.NotificationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/simulator")
@RequiredArgsConstructor
public class SimulatorController {

    private final KafkaSimulatorProducer simulatorProducer;

    @PostMapping("/send")
    public ResponseEntity<?> sendSingleEvent(@RequestBody(required = false) NotificationEvent customEvent) {
        NotificationEvent event = simulatorProducer.sendSingleSimulatedEvent(customEvent);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Simulated event dispatched to Kafka successfully",
                "event", event
        ));
    }

    @PostMapping("/batch")
    public ResponseEntity<?> sendBatchEvents(@RequestParam(defaultValue = "10") int count) {
        List<NotificationEvent> events = simulatorProducer.sendBatchSimulatedEvents(count);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Dispatched " + events.size() + " simulated events to Kafka",
                "count", events.size(),
                "events", events
        ));
    }

    @PostMapping("/start")
    public ResponseEntity<?> startSimulation(@RequestParam(defaultValue = "2") int ratePerSec) {
        boolean started = simulatorProducer.startSimulation(ratePerSec);
        return ResponseEntity.ok(Map.of(
                "status", started ? "SUCCESS" : "ALREADY_RUNNING",
                "message", started ? "Kafka Simulator producer started at " + ratePerSec + " events/sec" : "Simulator is already active",
                "details", simulatorProducer.getStatus()
        ));
    }

    @PostMapping("/stop")
    public ResponseEntity<?> stopSimulation() {
        boolean stopped = simulatorProducer.stopSimulation();
        return ResponseEntity.ok(Map.of(
                "status", stopped ? "SUCCESS" : "NOT_RUNNING",
                "message", stopped ? "Kafka Simulator producer stopped" : "Simulator was not running",
                "details", simulatorProducer.getStatus()
        ));
    }

    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        return ResponseEntity.ok(simulatorProducer.getStatus());
    }
}
