package com.example.print.controller;

import com.example.print.model.PrintClient;
import com.example.print.service.PrintClientService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 *
 */
@RestController
@RequestMapping("/api/clients")
@Slf4j
public class PrintClientController {

    @Autowired
    private PrintClientService clientService;

    @PostMapping("/register")
    public ResponseEntity<PrintClient> registerClient(@RequestBody PrintClient client) {
        PrintClient registeredClient = clientService.registerClient(client);
        return ResponseEntity.ok(registeredClient);
    }

    @PutMapping("/{clientId}/heartbeat")
    public ResponseEntity<Void> updateHeartbeat(@PathVariable String clientId) {
        PrintClient client = clientService.updateHeartbeat(clientId);

        if (client != null) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<PrintClient>> getClients(
            @RequestParam(required = false) String merchantId) {

        List<PrintClient> clients;
        if (merchantId != null && !merchantId.isEmpty()) {
            clients = clientService.findAvailableClientsByMerchant(Integer.valueOf(merchantId));
        } else {
            clients = clientService.findAvailableClientsByMerchant(null);
        }

        return ResponseEntity.ok(clients);
    }
}