package io.skalogs.skaetl.service;

import io.prometheus.client.Gauge;
import io.skalogs.skaetl.config.ProcessConfiguration;
import io.skalogs.skaetl.domain.*;
import io.skalogs.skaetl.repository.ConsumerStateRepository;
import io.skalogs.skaetl.repository.WorkerRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class RegistryService {

    private final ProcessConfiguration processConfiguration;
    private final WorkerRepository workerRepository;
    private final ConsumerStateRepository consumerStateRepository;

    public static final Gauge worker = Gauge.build()
            .name("nb_worker")
            .help("nb worker")
            .labelNames("status")
            .register();

    public RegistryService(ProcessConfiguration processConfiguration, WorkerRepository workerRepository, ConsumerStateRepository consumerStateRepository) {
        this.processConfiguration = processConfiguration;
        this.workerRepository = workerRepository;
        this.consumerStateRepository = consumerStateRepository;
    }

    public List<RegistryWorker> getAllStatus() {
        return workerRepository.findAll();
    }

    // WORKERS APIs

    public void addHost(RegistryWorker registryWorker) {
        log.info("Registering {} as {}", registryWorker.getName(), registryWorker.getWorkerType());
        workerRepository.save(RegistryWorker.builder()
                .name(registryWorker.getName())
                .port(registryWorker.getPort())
                .dateRefresh(new Date())
                .ip(registryWorker.getIp())
                .workerType(registryWorker.getWorkerType())
                .status(StatusWorker.OK)
                .statusConsumerList(registryWorker.getStatusConsumerList())
                .build());
        worker.labels(StatusWorker.OK.name()).inc();
    }

    public void refresh(RegistryWorker registryWorker) {
        RegistryWorker registry = workerRepository.findByKey(registryWorker.getFQDN());
        if (registry == null) {
            log.error("Refresh but not registry for item {}", registryWorker);
        } else {
            registry.setStatus(statusWorker(registry.getDateRefresh(), registryWorker));
            registry.setDateRefresh(new Date());
            registry.setPort(registryWorker.getPort());
            workerRepository.save(registry);
        }
    }

    // APIs that should be used by etl-backend

    public ConsumerState findConsumerStateById(String id) {
        return consumerStateRepository.findByKey(id);
    }

    public ProcessDefinition findById(String id) {
        ConsumerState processDefinition = consumerStateRepository.findByKey(id);
        if (processDefinition == null) {
            return null;
        }
        return processDefinition.getProcessDefinition();
    }

    public List<ConsumerState> findAll(WorkerType workerType) {
        return consumerStateRepository.findAll().stream()
                .filter(consumerState -> consumerState.getWorkerType() == workerType)
                .collect(Collectors.toList());
    }

    public void activate(ProcessDefinition processDefinition) {
        ConsumerState consumerState = consumerStateRepository.findByKey(processDefinition.getIdProcess()).withProcessDefinition(processDefinition);
        consumerState = assignConsumerToWorkers(consumerState);
        triggerAction(consumerState, "activate", StatusProcess.ENABLE, StatusProcess.ERROR);

    }

    public void remove(ProcessDefinition processDefinition) {
        deactivate(processDefinition);
        consumerStateRepository.deleteByKey(processDefinition.getIdProcess());
    }

    public void deactivate(ProcessDefinition processDefinition) {
        ConsumerState consumerState = consumerStateRepository.findByKey(processDefinition.getIdProcess()).withProcessDefinition(processDefinition);
        triggerAction(consumerState, "deactivate", StatusProcess.DISABLE, StatusProcess.DISABLE);
    }

    public void register(ProcessDefinition processDefinition, WorkerType workerType, StatusProcess statusProcess) {
        ConsumerState consumerState = new ConsumerState(processDefinition, workerType, statusProcess);
        consumerStateRepository.save(consumerState);
    }

    public void updateStatus(String idProcess, StatusProcess statusProcess) {
        ConsumerState consumerState = consumerStateRepository.findByKey(idProcess);
        consumerStateRepository.save(consumerState.withStatusProcess(statusProcess));
    }

    public void updateProcessDefinition(ProcessDefinition processDefinition) {
        ConsumerState consumerState = consumerStateRepository.findByKey(processDefinition.getIdProcess());
        consumerStateRepository.save(consumerState.withProcessDefinition(processDefinition));
    }

    public void createOrUpdateProcessDefinition(ProcessDefinition processDefinition, WorkerType workerType, StatusProcess statusProcess) {
        ConsumerState consumerState = consumerStateRepository.findByKey(processDefinition.getIdProcess());
        if (consumerState == null) {
            consumerState = new ConsumerState(processDefinition, workerType, statusProcess);
            consumerStateRepository.save(consumerState);
        } else {
            consumerStateRepository.save(consumerState.withProcessDefinition(processDefinition));
        }
    }

    // Internal apis
    private RegistryWorker getWorkerAvailable(WorkerType workerType) throws Exception {
        return workerRepository.findAll().stream()
                .filter(e -> e.getWorkerType() == workerType)
                .filter(e -> e.getStatus() == StatusWorker.OK)
                .findFirst().orElseThrow(() -> new Exception("No Worker Available"));
    }

    private void triggerAction(ConsumerState consumerState, String action, StatusProcess statusIfOk, StatusProcess statusIfKo) {
        boolean hasErrors = consumerState.getStatusProcess() == StatusProcess.ERROR;
        for (String workerFQDN : consumerState.getRegistryWorkers()) {
            RegistryWorker worker = workerRepository.findByKey(workerFQDN);
            log.info("triggering {} on {}", action, consumerState.getProcessDefinition());
            try {
                RestTemplate restTemplate = new RestTemplate();
                HttpEntity<ProcessDefinition> request = new HttpEntity<>(consumerState.getProcessDefinition());
                restTemplate.postForObject(worker.getBaseUrl() + "/manage/" + action, request, String.class);
            } catch (RestClientException e) {
                log.error("an error occured while triggering" + action + " on " + consumerState.getProcessDefinition(), e.getMessage());
                hasErrors = true;
            }
        }

        ConsumerState newState;
        if (!hasErrors) {
            newState = consumerState.withStatusProcess(statusIfOk);
        } else {
            newState = consumerState.withStatusProcess(statusIfKo);
        }
        consumerStateRepository.save(newState);
    }

    private ConsumerState assignConsumerToWorkers(ConsumerState consumerState) {
        StatusProcess result = StatusProcess.INIT;
        try {
            int nbWorkerToAssign = consumerState.getNbInstance() - consumerState.getRegistryWorkers().size();
            for (int i = 0; i < nbWorkerToAssign; i++) {
                consumerState.getRegistryWorkers().add(getWorkerAvailable(consumerState.getWorkerType()).getFQDN());
            }

        } catch (Exception e) {
            log.error("No Worker available for {}", consumerState.getProcessDefinition());
            result = StatusProcess.ERROR;
        }
        ConsumerState newState = consumerState.withStatusProcess(result);
        consumerStateRepository.save(newState);

        return newState;
    }

    @Scheduled(initialDelay = 1 * 60 * 1000, fixedRate = 1 * 60 * 1000)
    public void checkWorkersAlive() {
        log.info("checkWorkersAlive");
        List<RegistryWorker> workers = workerRepository.findAll();
        workers.stream()
                .filter(registry -> registry.getStatus() == StatusWorker.OK)
                .forEach(registry -> registry.setStatus(statusWorker(registry.getDateRefresh(), registry)));
        rescheduleConsumerFromDeadWorkers();
        rescheduleConsumersInError();
        worker.labels(StatusWorker.OK.name()).set(workers.stream()
                .filter(registryWorker -> registryWorker.getStatus() == StatusWorker.OK)
                .count());
        worker.labels(StatusWorker.KO.name()).set(workers.stream()
                .filter(registryWorker -> registryWorker.getStatus() == StatusWorker.KO)
                .count());
        worker.labels(StatusWorker.FULL.name()).set(workers.stream()
                .filter(registryWorker -> registryWorker.getStatus() == StatusWorker.FULL)
                .count());
    }

    private void rescheduleConsumerFromDeadWorkers() {
        workerRepository.findAll()
                .stream()
                .filter(worker -> worker.getStatus() == StatusWorker.KO)
                .forEach(this::rescheduleConsumerFromDeadWorker);
    }

    private void rescheduleConsumerFromDeadWorker(RegistryWorker registryWorker) {
        List<StatusConsumer> consumerList = registryWorker.getStatusConsumerList();
        for (StatusConsumer statusConsumer : consumerList) {
            ConsumerState consumerState = consumerStateRepository.findByKey(statusConsumer.getIdProcessConsumer());
            //remove it from running workers
            consumerState.getRegistryWorkers().remove(registryWorker.getFQDN());
            //elect new worker
            rescheduleProcessDefinition(consumerState);
        }
    }

    private void rescheduleProcessDefinition(ConsumerState consumerState) {
        log.info("rescheduling {}", consumerState.getProcessDefinition());
        //run it
        activate(consumerState.getProcessDefinition());
    }

    private void rescheduleConsumersInError() {
        consumerStateRepository.findAll().stream()
                .filter(consumerState -> consumerState.getStatusProcess() == StatusProcess.ERROR)
                .forEach(this::rescheduleProcessDefinition);
    }

    private StatusWorker statusWorker(Date lastRefresh, RegistryWorker registryWorker) {
        //too many consumer
        if (registryWorker.getStatusConsumerList() != null && registryWorker.getStatusConsumerList().size() > processConfiguration.getMaxProcessConsumer()) {
            return StatusWorker.FULL;
        }
        Date actual = new Date();
        LocalDateTime lActual = LocalDateTime.ofInstant(actual.toInstant(), ZoneId.systemDefault());
        LocalDateTime lastCurrent = LocalDateTime.ofInstant(lastRefresh.toInstant(), ZoneId.systemDefault());
        if (lastCurrent.plusMinutes(5).plusSeconds(10).isBefore(lActual)) {
            return StatusWorker.KO;
        }
        return StatusWorker.OK;
    }
}