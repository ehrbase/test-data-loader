/*
 * Copyright 2022 vitasystems GmbH and Hannover Medical School.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ehrbase.testdata.loader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nedap.archie.json.JacksonUtil;
import com.nedap.archie.rm.composition.AdminEntry;
import com.nedap.archie.rm.composition.CareEntry;
import com.nedap.archie.rm.composition.Composition;
import com.nedap.archie.rm.composition.EventContext;
import com.nedap.archie.rm.composition.Section;
import com.nedap.archie.rm.datatypes.CodePhrase;
import com.nedap.archie.rm.datavalues.DvCodedText;
import com.nedap.archie.rm.datavalues.DvText;
import com.nedap.archie.rm.datavalues.TermMapping;
import com.nedap.archie.rm.generic.Participation;
import com.nedap.archie.rm.generic.PartyIdentified;
import com.nedap.archie.rm.generic.PartyProxy;
import org.ehrbase.serialisation.dbencoding.RawJson;
import org.ehrbase.testdata.loader.config.LoaderProperties;
import org.ehrbase.testdata.loader.jooq.enums.ContributionChangeType;
import org.ehrbase.testdata.loader.jooq.enums.ContributionDataType;
import org.ehrbase.testdata.loader.jooq.enums.ContributionState;
import org.ehrbase.testdata.loader.jooq.enums.EntryType;
import org.ehrbase.testdata.loader.jooq.enums.PartyRefIdType;
import org.ehrbase.testdata.loader.jooq.enums.PartyType;
import org.ehrbase.testdata.loader.jooq.tables.Ehr;
import org.ehrbase.testdata.loader.jooq.tables.Identifier;
import org.ehrbase.testdata.loader.jooq.tables.System;
import org.ehrbase.testdata.loader.jooq.tables.records.AuditDetailsRecord;
import org.ehrbase.testdata.loader.jooq.tables.records.CompositionRecord;
import org.ehrbase.testdata.loader.jooq.tables.records.ContributionRecord;
import org.ehrbase.testdata.loader.jooq.tables.records.EhrRecord;
import org.ehrbase.testdata.loader.jooq.tables.records.EntryRecord;
import org.ehrbase.testdata.loader.jooq.tables.records.EventContextRecord;
import org.ehrbase.testdata.loader.jooq.tables.records.ParticipationRecord;
import org.ehrbase.testdata.loader.jooq.tables.records.StatusRecord;
import org.ehrbase.testdata.loader.jooq.tables.records.TerritoryRecord;
import org.ehrbase.testdata.loader.jooq.udt.records.CodePhraseRecord;
import org.ehrbase.testdata.loader.jooq.udt.records.DvCodedTextRecord;
import org.ehrbase.testdata.loader.utils.FileUtils;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StopWatch;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.ehrbase.testdata.loader.jooq.tables.AuditDetails.AUDIT_DETAILS;
import static org.ehrbase.testdata.loader.jooq.tables.Composition.COMPOSITION;
import static org.ehrbase.testdata.loader.jooq.tables.Contribution.CONTRIBUTION;
import static org.ehrbase.testdata.loader.jooq.tables.Entry.ENTRY;
import static org.ehrbase.testdata.loader.jooq.tables.EventContext.EVENT_CONTEXT;
import static org.ehrbase.testdata.loader.jooq.tables.Participation.PARTICIPATION;
import static org.ehrbase.testdata.loader.jooq.tables.PartyIdentified.PARTY_IDENTIFIED;
import static org.ehrbase.testdata.loader.jooq.tables.Status.STATUS;
import static org.ehrbase.testdata.loader.jooq.tables.TemplateStore.TEMPLATE_STORE;
import static org.ehrbase.testdata.loader.jooq.tables.Territory.TERRITORY;

/**
 * @author Renaud Subiger
 * @since 1.0
 */
@SpringBootApplication
@EnableConfigurationProperties(LoaderProperties.class)
public class LoaderApplication implements CommandLineRunner {

    private final Logger log = LoggerFactory.getLogger(LoaderApplication.class);

    private final Random random = new Random();
    private final List<Composition> compositions = new ArrayList<>();

    private final ObjectMapper objectMapper = JacksonUtil.getObjectMapper();
    private final RawJson rawJson = new RawJson();

    private final DSLContext dsl;
    private final LoaderProperties properties;

    private UUID systemId;
    private UUID committerId;
    private String zoneId;

    public LoaderApplication(DSLContext dsl, LoaderProperties properties) {
        this.dsl = dsl;
        this.properties = properties;
    }

    public static void main(String[] args) {
        SpringApplication.run(LoaderApplication.class, args);
    }

    @PostConstruct
    public void initialize() throws IOException {
        zoneId = ZoneId.systemDefault().toString();
        systemId = getSystemId();
        committerId = getCommitterId();

        initializeTemplates();
        initializeCompositions();
    }

    private void initializeTemplates() throws IOException {
        createTemplate("Corona_Anamnese", "templates/corona_anamnese.opt");
        createTemplate("ehrbase_blood_pressure_simple.de.v0", "templates/ehrbase_blood_pressure.opt");
        createTemplate("International Patient Summary", "templates/international_patient_summary.opt");
        createTemplate("Virologischer Befund", "templates/virologischer_befund.opt");
    }

    private void initializeCompositions() {
        List<String> compositionFiles = List.of(
                "compositions/blood_pressure.json",
                "compositions/international_patient_summary.json",
                "compositions/corona_anamnese.json",
                "compositions/virologischer_befund.json"
        );

        compositionFiles.forEach(location -> {
            try (var in = FileUtils.getInputStream(location)) {
                compositions.add(objectMapper.readValue(in, Composition.class));
            } catch (IOException e) {
                throw new LoaderException("Failed to read composition file", e);
            }
        });
    }

    @Override
    public void run(String... args) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        log.info("Start loading test data... ({} EHRs, {} compositions)", properties.getEhr(),
                properties.getEhr() * properties.getCompositionPerEhr());

        IntStream.rangeClosed(1, properties.getEhr())
                .parallel()
                .forEach(i -> {
                    UUID ehrId = insertEhr();
                    insertCompositions(ehrId);
                });

        stopWatch.stop();
        log.info("Test data loaded in {} s", stopWatch.getTotalTimeSeconds());
    }

    public void insertCompositions(UUID ehrId) {
        IntStream.rangeClosed(1, properties.getCompositionPerEhr())
                .forEach(i -> {
                    var composition = getRandomComposition();
                    var compositionId = createComposition(ehrId, composition);
                    createEntry(compositionId, composition);
                    if (composition.getContext() != null) {
                        var eventContextId = createEventContext(compositionId, composition.getContext());
                        createParticipations(eventContextId, composition.getContext().getParticipations());
                    }
                });
    }

    public UUID insertEhr() {
        var ehrId = createEhr();
        createStatus(ehrId);
        return ehrId;
    }

    /**
     * Creates an {@link EhrRecord}.
     */
    private UUID createEhr() {
        var ehrRecord = dsl.newRecord(Ehr.EHR_);
        ehrRecord.setDateCreated(LocalDateTime.now());
        ehrRecord.setDateCreatedTzid(zoneId);
        ehrRecord.setSystemId(systemId);
        ehrRecord.store();
        log.trace("Created EHR: {}", ehrRecord.getId());
        return ehrRecord.getId();
    }

    /**
     * Creates an {@link StatusRecord} for the given EHR.
     */
    private void createStatus(UUID ehrId) {
        var partyRecord = dsl.newRecord(PARTY_IDENTIFIED);
        partyRecord.setPartyRefValue(UUID.randomUUID().toString());
        partyRecord.setPartyRefScheme("id_scheme");
        partyRecord.setPartyRefNamespace("patients");
        partyRecord.setPartyRefType("PERSON");
        partyRecord.setPartyType(PartyType.party_self);
        partyRecord.setObjectIdType(PartyRefIdType.generic_id);
        partyRecord.store();

        var statusRecord = dsl.newRecord(STATUS);
        statusRecord.setEhrId(ehrId);
        statusRecord.setParty(partyRecord.getId());
        statusRecord.setSysTransaction(LocalDateTime.now());
        statusRecord.setSysPeriod(new AbstractMap.SimpleEntry<>(OffsetDateTime.now(), null));
        statusRecord.setHasAudit(createAuditDetails("Create EHR_STATUS"));
        statusRecord.setInContribution(createContribution(ehrId, ContributionDataType.ehr, "Create EHR_STATUS"));
        statusRecord.setArchetypeNodeId("openEHR-EHR-ITEM_TREE.fake.v1");
        statusRecord.setName(new DvCodedTextRecord("Created by Test Data Loader", null, null, null, null, null));

        try {
            statusRecord.setOtherDetails(JSONB.jsonb(FileUtils.getContent("ehr_status/ehr_status.json")));
        } catch (IOException e) {
            throw new LoaderException("Failed to read EHR_STATUS file", e);
        }

        statusRecord.store();
        log.trace("Created EHR_STATUS: {}", statusRecord.getId());
    }

    private Composition getRandomComposition() {
        return compositions.get(random.nextInt(4));
    }

    private UUID getSystemId() {
        var system = dsl.fetchOne(System.SYSTEM);
        if (system == null) {
            system = dsl.newRecord(System.SYSTEM);
            system.setDescription("Default system");
            system.setSettings("local.ehrbase.org");
            system.store();
        }
        return system.getId();
    }

    private UUID getCommitterId() {
        var committerRecord =
                dsl.fetchOne(PARTY_IDENTIFIED, PARTY_IDENTIFIED.NAME.eq("EHRbase Internal Test Data Loader"));

        if (committerRecord == null) {
            committerRecord = dsl.newRecord(PARTY_IDENTIFIED);
            committerRecord.setName("EHRbase Internal Test Data Loader");
            committerRecord.setPartyRefValue(UUID.randomUUID().toString());
            committerRecord.setPartyRefScheme("DEMOGRAPHIC");
            committerRecord.setPartyRefNamespace("User");
            committerRecord.setPartyRefType("PARTY");
            committerRecord.setPartyType(PartyType.party_identified);
            committerRecord.setObjectIdType(PartyRefIdType.generic_id);
            committerRecord.store();

            var identifierRecord = dsl.newRecord(Identifier.IDENTIFIER);
            identifierRecord.setIdValue("Test Data Loader");
            identifierRecord.setIssuer("EHRbase");
            identifierRecord.setAssigner("EHRbase");
            identifierRecord.setTypeName("EHRbase Security Authentication User");
            identifierRecord.setParty(committerRecord.getId());
            dsl.insertInto(Identifier.IDENTIFIER)
                    .set(identifierRecord)
                    .execute();
        }
        return committerRecord.getId();
    }

    private void createTemplate(String templateId, String resourceLocation) throws IOException {
        var existingTemplateStore =
                dsl.fetchOptional(TEMPLATE_STORE, TEMPLATE_STORE.TEMPLATE_ID.eq(templateId));

        if (existingTemplateStore.isPresent()) {
            log.info("Template {} already exists", templateId);
        } else {
            var templateStoreRecord = dsl.newRecord(TEMPLATE_STORE);
            templateStoreRecord.setId(UUID.randomUUID());
            templateStoreRecord.setTemplateId(templateId);
            templateStoreRecord.setContent(FileUtils.getContent(resourceLocation));
            templateStoreRecord.setSysTransaction(LocalDateTime.now());
            templateStoreRecord.store();
        }
    }

    private UUID createPartyIdentified(PartyProxy composer) {
        var partyIdentifiedRecord = dsl.newRecord(PARTY_IDENTIFIED);

        if (composer instanceof PartyIdentified ) {
            partyIdentifiedRecord.setName(((PartyIdentified) composer).getName());
            partyIdentifiedRecord.setPartyType(PartyType.party_identified);
            partyIdentifiedRecord.setObjectIdType(PartyRefIdType.undefined);
            partyIdentifiedRecord.store();
            return partyIdentifiedRecord.getId();
        } else {
            throw new IllegalArgumentException("Unsupported PartyProxy implementation");
        }
    }

    /**
     * Creates a {@link CompositionRecord} for the given EHR.
     */
    private UUID createComposition(UUID ehrId, Composition composition) {
        var compositionRecord = dsl.newRecord(COMPOSITION);
        compositionRecord.setEhrId(ehrId);
        compositionRecord.setInContribution(createContribution(ehrId, ContributionDataType.composition, "Create COMPOSITION"));
        compositionRecord.setLanguage(composition.getLanguage().getCodeString());
        compositionRecord.setTerritory(getTerritory(composition.getTerritory().getCodeString()));
        compositionRecord.setComposer(createPartyIdentified(composition.getComposer()));
        compositionRecord.setSysTransaction(LocalDateTime.now());
        compositionRecord.setSysPeriod(new AbstractMap.SimpleEntry<>(OffsetDateTime.now(), null));
        compositionRecord.setHasAudit(createAuditDetails("Create COMPOSITION"));
        // AttestationRef
        // FeederAudit
        compositionRecord.setLinks(JSONB.jsonb("[]"));
        compositionRecord.store();
        return compositionRecord.getId();
    }

    /**
     * Creates an {@link EntryRecord} for the given composition.
     */
    private void createEntry(UUID compositionId, Composition composition) {
        Assert.notNull(composition.getArchetypeDetails().getTemplateId(), "Template Id must not be null");

        var entryRecord = dsl.newRecord(ENTRY);
        entryRecord.setCompositionId(compositionId);
        entryRecord.setSequence(0);
        entryRecord.setItemType(resolveEntryType(composition));
        entryRecord.setTemplateId(composition.getArchetypeDetails().getTemplateId().getValue());
        entryRecord.setArchetypeId(composition.getArchetypeNodeId());
        entryRecord.setCategory(createDvCodedText(composition.getCategory()));
        entryRecord.setEntry(JSONB.jsonb(rawJson.marshal(composition)));
        entryRecord.setSysTransaction(LocalDateTime.now());
        entryRecord.setSysPeriod(new AbstractMap.SimpleEntry<>(OffsetDateTime.now(), null));
        entryRecord.setRmVersion(composition.getArchetypeDetails().getRmVersion());
        entryRecord.setName(createDvCodedText(composition.getName()));
        entryRecord.store();
    }

    /**
     * Creates an {@link EventContextRecord} for the given composition.
     */
    private UUID createEventContext(UUID compositionId, EventContext eventContext) {
        var eventContextRecord = dsl.newRecord(EVENT_CONTEXT);
        eventContextRecord.setCompositionId(compositionId);

        var startTime = eventContext.getStartTime().getValue();
        eventContextRecord.setStartTime(LocalDateTime.from(startTime));
        eventContextRecord.setStartTimeTzid(resolveTimeZone(startTime));
        eventContextRecord.setLocation(eventContext.getLocation());
        eventContextRecord.setSetting(createDvCodedText(eventContext.getSetting()));
        eventContextRecord.setSysTransaction(LocalDateTime.now());
        eventContextRecord.setSysPeriod(new AbstractMap.SimpleEntry<>(OffsetDateTime.now(), null));
        // Facility

        if (eventContext.getEndTime() != null) {
            var endTime = eventContext.getEndTime().getValue();
            eventContextRecord.setEndTime(LocalDateTime.from(endTime));
            eventContextRecord.setEndTimeTzid(resolveTimeZone(endTime));
        }

        if (eventContext.getOtherContext() != null && !CollectionUtils.isEmpty(eventContext.getOtherContext().getItems())) {
            eventContextRecord.setOtherContext(JSONB.jsonb(rawJson.marshal(eventContext.getOtherContext())));
        }

        eventContextRecord.store();
        return eventContextRecord.getId();
    }

    /**
     * Creates a {@link ParticipationRecord} for the given event context.
     */
    private void createParticipations(UUID eventContextId, List<Participation> participations) {
        for (var participation : participations) {
            var participationRecord = dsl.newRecord(PARTICIPATION);
            participationRecord.setEventContext(eventContextId);
            participationRecord.setPerformer(createPartyIdentified(participation.getPerformer()));
            participationRecord.setFunction(createDvCodedText(participation.getFunction()));
            participationRecord.setMode(createDvCodedText(participation.getMode()));
            participationRecord.setSysTransaction(LocalDateTime.now());
            participationRecord.setSysPeriod(new AbstractMap.SimpleEntry<>(OffsetDateTime.now(), null));
            if (participation.getTime() != null && participation.getTime().getLower() != null) {
                var lower = participation.getTime().getLower().getValue();
                participationRecord.setTimeLower(LocalDateTime.from(lower));
                participationRecord.setTimeLowerTz(resolveTimeZone(lower));
            }
            if (participation.getTime() != null && participation.getTime().getUpper() != null) {
                var upper = participation.getTime().getUpper().getValue();
                participationRecord.setTimeUpper(LocalDateTime.from(upper));
                participationRecord.setTimeUpperTz(resolveTimeZone(upper));
            }
            participationRecord.store();
        }
    }

    /**
     * Creates a {@link ContributionRecord} of the given EHR.
     */
    private UUID createContribution(UUID ehrId, ContributionDataType contributionType, String auditDetailsDescription) {
        var contributionRecord = dsl.newRecord(CONTRIBUTION);
        contributionRecord.setEhrId(ehrId);
        contributionRecord.setContributionType(contributionType);
        contributionRecord.setState(ContributionState.complete);
        contributionRecord.setHasAudit(createAuditDetails(auditDetailsDescription));
        contributionRecord.store();
        return contributionRecord.getId();
    }

    /**
     * Creates an {@link AuditDetailsRecord} with the given description.
     */
    private UUID createAuditDetails(String description) {
        var auditDetailsRecord = dsl.newRecord(AUDIT_DETAILS);
        auditDetailsRecord.setSystemId(systemId);
        auditDetailsRecord.setCommitter(committerId);
        auditDetailsRecord.setTimeCommitted(LocalDateTime.now());
        auditDetailsRecord.setTimeCommittedTzid(zoneId);
        auditDetailsRecord.setChangeType(ContributionChangeType.creation);
        auditDetailsRecord.setDescription(description);
        auditDetailsRecord.store();
        return auditDetailsRecord.getId();
    }

    private DvCodedTextRecord createDvCodedText(DvText dvText) {
        if (dvText == null) {
            return null;
        }

        var dvCodedTextRecord = new DvCodedTextRecord();
        dvCodedTextRecord.setValue(dvText.getValue());
        dvCodedTextRecord.setFormatting(dvText.getFormatting());
        dvCodedTextRecord.setLanguage(createCodePhrase(dvText.getLanguage()));
        dvCodedTextRecord.setEncoding(createCodePhrase(dvText.getEncoding()));
        dvCodedTextRecord.setTermMapping(createTermMappings(dvText.getMappings()));

        if (dvText instanceof DvCodedText ) {
            dvCodedTextRecord.setDefiningCode(createCodePhrase(((DvCodedText) dvText).getDefiningCode()));
        }

        return dvCodedTextRecord;
    }

    private CodePhraseRecord createCodePhrase(CodePhrase codePhrase) {
        if (codePhrase == null) {
            return null;
        }
        return new CodePhraseRecord(codePhrase.getTerminologyId().getValue(), codePhrase.getCodeString());
    }

    private String[] createTermMappings(List<TermMapping> termMappings) {
        if (CollectionUtils.isEmpty(termMappings)) {
            return new String[0];
        }

        return termMappings.stream()
                .map(termMapping -> {
                    String result = termMapping.getMatch() + "|";

                    if (termMapping.getPurpose() != null) {
                        result += termMapping.getPurpose().getValue() + "|";
                    }

                    result += termMapping.getPurpose().getDefiningCode().getTerminologyId().getValue() + "|" +
                            termMapping.getPurpose().getDefiningCode().getCodeString() + "|" +
                            termMapping.getTarget().getTerminologyId().getValue() + "|" +
                            termMapping.getTarget().getCodeString();

                    return result;
                })
                .toArray( String[]::new);
    }

    private Integer getTerritory(String code) {
        return dsl.fetchOptional(TERRITORY, TERRITORY.TWOLETTER.eq(code))
                .map(TerritoryRecord::getCode)
                .orElseThrow(() -> new IllegalArgumentException("Territory " + code + " not found"));
    }

    private EntryType resolveEntryType(Composition composition) {
        if (CollectionUtils.isEmpty(composition.getContent())) {
            return EntryType.proxy; // FIXME: not sure which value to return
        }

        var contentItem = composition.getContent().get(0);
        if (contentItem instanceof AdminEntry) {
            return EntryType.admin;
        } else if (contentItem instanceof CareEntry) {
            return EntryType.care_entry;
        } else if (contentItem instanceof Section) {
            return EntryType.section;
        } else {
            return EntryType.proxy; // FIXME: not sure which value to return
        }
    }

    private String resolveTimeZone(TemporalAccessor temporal) {
        if (temporal == null) {
            return null;
        }

        if (temporal instanceof ZonedDateTime ) {
            return ((ZonedDateTime) temporal).getZone().toString();
        } else if (temporal instanceof OffsetDateTime ) {
            return ((OffsetDateTime) temporal).getOffset().toString();
        } else {
            return zoneId;
        }
    }
}
