package org.openmrs.api.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.openmrs.*;
import org.openmrs.api.db.EncounterDAO;
import org.openmrs.test.jupiter.BaseContextMockTest;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.ServiceContext;
import org.openmrs.api.context.UsernamePasswordCredentials;

import org.openmrs.api.AdministrationService;
import org.openmrs.api.ConditionService;
import org.openmrs.api.DiagnosisService;
import org.openmrs.api.EncounterService;
import org.openmrs.api.ObsService;
import org.openmrs.api.OrderService;
import org.openmrs.api.PatientService;

@MockitoSettings(strictness = Strictness.LENIENT)
public class EncounterServiceImplTest extends BaseContextMockTest {

    private EncounterServiceImpl service;

    @Mock private EncounterDAO dao;
    @Mock private OrderService orderService;
    @Mock private ObsService obsService;
    @Mock private ConditionService conditionService;
    @Mock private PatientService patientService;
    @Mock private DiagnosisService diagnosisService;
    @Mock private AdministrationService adminService;

    @BeforeEach
    public void setup() {
        Context.authenticate(new UsernamePasswordCredentials("admin", "admin"));
        service = spy(new EncounterServiceImpl());
        service.setEncounterDAO(dao);

        lenient().doAnswer(inv -> inv.getArgument(0))
                 .when(service).filterEncountersByViewPermissions(anyList(), any());
        lenient().doAnswer(inv -> null)
                 .when(service).requirePrivilege(any(Encounter.class));
        lenient().doAnswer(inv -> null)
                 .when(service).addGivenObsAndTheirGroupMembersToEncounter(anyCollection(), any());

        ServiceContext ctx = ServiceContext.getInstance();
        ctx.setService(EncounterService.class, service);
        ctx.setService(OrderService.class, orderService);
        ctx.setService(ObsService.class, obsService);
        ctx.setService(ConditionService.class, conditionService);
        ctx.setService(PatientService.class, patientService);
        ctx.setService(DiagnosisService.class, diagnosisService);
        ctx.setService(AdministrationService.class, adminService);
    }

    @Test
    public void nullPatientId_throws() {
        assertThrows(IllegalArgumentException.class, () -> service.getEncountersByPatientId(null));
    }

    @Test
    public void negativeId_returnsEmpty() {
        when(dao.getEncountersByPatientId(-5)).thenReturn(Collections.emptyList());
        assertTrue(service.getEncountersByPatientId(-5).isEmpty());
    }

    @Test
    public void zeroId_returnsEmpty() {
        when(dao.getEncountersByPatientId(0)).thenReturn(Collections.emptyList());
        assertTrue(service.getEncountersByPatientId(0).isEmpty());
    }

    @Test
    public void validId_noEncounters() {
        when(dao.getEncountersByPatientId(123)).thenReturn(Collections.emptyList());
        assertTrue(service.getEncountersByPatientId(123).isEmpty());
    }

    @Test
    public void validId_withEncounters_noFilter() {
        Encounter e1 = new Encounter(), e2 = new Encounter();
        when(dao.getEncountersByPatientId(123)).thenReturn(Arrays.asList(e1, e2));
        List<Encounter> result = service.getEncountersByPatientId(123);
        assertEquals(2, result.size());
        assertSame(e1, result.get(0));
        assertSame(e2, result.get(1));
    }

    @Test
    public void validId_withEncounters_filterAllOut() {
        Encounter e1 = new Encounter(), e2 = new Encounter();
        List<Encounter> raw = Arrays.asList(e1, e2);
        when(dao.getEncountersByPatientId(123)).thenReturn(raw);
        doReturn(Collections.emptyList())
            .when(service).filterEncountersByViewPermissions(raw, null);
        assertTrue(service.getEncountersByPatientId(123).isEmpty());
    }

    @Test
    public void validId_withEncounters_filterSubset() {
        Encounter e1 = new Encounter(), e2 = new Encounter(), e3 = new Encounter();
        List<Encounter> raw = Arrays.asList(e1, e2, e3);
        when(dao.getEncountersByPatientId(123)).thenReturn(raw);
        doReturn(Arrays.asList(e2))
            .when(service).filterEncountersByViewPermissions(raw, null);
        List<Encounter> result = service.getEncountersByPatientId(123);
        assertEquals(1, result.size());
        assertSame(e2, result.get(0));
    }

    @Test
    public void hugeId_returnsEmpty() {
        when(dao.getEncountersByPatientId(Integer.MAX_VALUE)).thenReturn(Collections.emptyList());
        assertTrue(service.getEncountersByPatientId(Integer.MAX_VALUE).isEmpty());
    }

    private Person createPerson(int id) {
        Person p = new Person();
        p.setPersonId(id);
        return p;
    }

    @Test
    public void save_newEncounter_skipsUpdates() {
        Patient patient = new Patient(); patient.setPatientId(1);
        Encounter enc = new Encounter();
        enc.setPatient(patient);
        enc.setEncounterType(new EncounterType());
        enc.setEncounterDatetime(new Date());
        Location loc = new Location(); loc.setLocationId(10);
        enc.setLocation(loc);

        Obs obs = new Obs();
        obs.setId(null);
        obs.setObsDatetime(enc.getEncounterDatetime());
        obs.setLocation(loc);
        enc.addObs(obs);

        Order order = new Order();
        Patient other = new Patient(); other.setPatientId(2);
        order.setPatient(other);
        enc.addOrder(order);

        when(dao.getSavedEncounterDatetime(enc)).thenReturn(enc.getEncounterDatetime());
        when(dao.getSavedEncounterLocation(enc)).thenReturn(loc);
        when(dao.saveEncounter(enc)).thenReturn(enc);

        Encounter result = service.saveEncounter(enc);
        assertEquals(enc, result);
        assertEquals(enc.getEncounterDatetime(), obs.getObsDatetime());
        assertEquals(patient, order.getPatient());
    }

    @Test
    public void save_existingEncounter_updatesObsAndOrders() {
        Patient patient = new Patient(); patient.setPatientId(1);
        Encounter enc = new Encounter();
        enc.setEncounterId(100);
        enc.setPatient(patient);
        enc.setEncounterType(new EncounterType());

        Date oldDate = new Date(0), newDate = new Date(1000);
        Location oldLoc = new Location(); oldLoc.setLocationId(10);
        Location newLoc = new Location(); newLoc.setLocationId(20);

        when(dao.getSavedEncounterDatetime(enc)).thenReturn(oldDate);
        when(dao.getSavedEncounterLocation(enc)).thenReturn(oldLoc);
        enc.setEncounterDatetime(newDate);
        enc.setLocation(newLoc);

        Obs obs = new Obs();
        obs.setId(200);
        obs.setObsDatetime(oldDate);
        obs.setLocation(oldLoc);
        obs.setPerson(createPerson(2));
        enc.addObs(obs);

        Order order = new Order();
        Patient other = new Patient(); other.setPatientId(2);
        order.setPatient(other);
        enc.addOrder(order);

        when(dao.saveEncounter(enc)).thenReturn(enc);

        Encounter result = service.saveEncounter(enc);
        assertEquals(newDate, obs.getObsDatetime());
        assertEquals(newLoc.getLocationId(), obs.getLocation().getLocationId());
        assertEquals(patient.getPerson().getPersonId(), obs.getPerson().getPersonId());
        assertEquals(patient, order.getPatient());
        assertEquals(enc, result);
    }

    @Test
    public void save_existingEncounter_noChange() {
        Patient patient = new Patient(); patient.setPatientId(1);
        Encounter enc = new Encounter();
        enc.setEncounterId(101);
        enc.setPatient(patient);
        enc.setEncounterType(new EncounterType());

        Date same = new Date(5000);
        Location loc = new Location(); loc.setLocationId(30);

        when(dao.getSavedEncounterDatetime(enc)).thenReturn(same);
        when(dao.getSavedEncounterLocation(enc)).thenReturn(loc);
        enc.setEncounterDatetime(same);
        enc.setLocation(loc);

        Obs obs = new Obs();
        obs.setId(300);
        obs.setObsDatetime(same);
        obs.setLocation(loc);
        obs.setPerson(patient.getPerson());
        enc.addObs(obs);

        Order order = new Order();
        order.setPatient(patient);
        enc.addOrder(order);

        when(dao.saveEncounter(enc)).thenReturn(enc);

        Encounter result = service.saveEncounter(enc);
        assertEquals(same, obs.getObsDatetime());
        assertEquals(loc.getLocationId(), obs.getLocation().getLocationId());
        assertEquals(patient.getPerson().getPersonId(), obs.getPerson().getPersonId());
        assertEquals(patient, order.getPatient());
        assertEquals(enc, result);
    }

    @Test
    public void save_CACC_majorA() {
        // Row 4: new encounter
        Patient p1 = new Patient(); p1.setPatientId(1);
        Encounter enc4 = new Encounter();
        enc4.setPatient(p1);
        enc4.setEncounterType(new EncounterType());
        enc4.setEncounterId(null);
        Date oldDate = new Date(0), newDate = new Date(1000);
        Location oldLoc = new Location(); oldLoc.setLocationId(10);
        enc4.setEncounterDatetime(newDate);
        enc4.setLocation(oldLoc);

        Obs o4 = new Obs(); o4.setId(200);
        o4.setObsDatetime(oldDate);
        o4.setLocation(oldLoc);
        enc4.addObs(o4);

        when(dao.getSavedEncounterDatetime(enc4)).thenReturn(oldDate);
        when(dao.getSavedEncounterLocation(enc4)).thenReturn(oldLoc);
        lenient().when(service.requirePrivilege(enc4)).thenReturn(true);
        when(dao.saveEncounter(enc4)).thenReturn(enc4);

        Encounter r4 = service.saveEncounter(enc4);
        assertEquals(oldDate, o4.getObsDatetime());

        // Row 8: existing encounter
        Patient p2 = new Patient(); p2.setPatientId(1);
        Encounter enc8 = new Encounter();
        enc8.setEncounterId(123);
        enc8.setPatient(p2);
        enc8.setEncounterType(new EncounterType());
        when(dao.getSavedEncounterDatetime(enc8)).thenReturn(oldDate);
        when(dao.getSavedEncounterLocation(enc8)).thenReturn(oldLoc);
        enc8.setEncounterDatetime(newDate);
        Location newLoc2 = new Location(); newLoc2.setLocationId(20);
        enc8.setLocation(newLoc2);

        Obs o8 = new Obs(); o8.setId(300);
        o8.setObsDatetime(oldDate);
        o8.setLocation(oldLoc);
        enc8.addObs(o8);
        when(dao.saveEncounter(enc8)).thenReturn(enc8);

        Encounter r8 = service.saveEncounter(enc8);
        assertEquals(newDate, o8.getObsDatetime());
    }

    @Test
    public void save_CACC_majorB() {
        Date date = new Date(500);
        Patient p = new Patient(); p.setPatientId(1);
        Encounter low = new Encounter();
        low.setEncounterId(1);
        low.setPatient(p);
        low.setEncounterType(new EncounterType());
        when(dao.getSavedEncounterDatetime(low)).thenReturn(date);
        low.setEncounterDatetime(date);

        Obs oLow = new Obs(); oLow.setId(10);
        oLow.setObsDatetime(date);
        low.addObs(oLow);
        when(dao.saveEncounter(low)).thenReturn(low);

        Encounter rLow = service.saveEncounter(low);
        assertEquals(date, oLow.getObsDatetime());

        Encounter high = new Encounter();
        high.setEncounterId(2);
        high.setPatient(p);
        high.setEncounterType(new EncounterType());
        Date oldD = new Date(0), newD = new Date(1000);
        when(dao.getSavedEncounterDatetime(high)).thenReturn(oldD);
        high.setEncounterDatetime(newD);

        Obs oHigh = new Obs(); oHigh.setId(20);
        oHigh.setObsDatetime(oldD);
        high.addObs(oHigh);
        when(dao.saveEncounter(high)).thenReturn(high);

        Encounter rHigh = service.saveEncounter(high);
        assertEquals(newD, oHigh.getObsDatetime());
    }

    @Test
    public void save_CACC_majorC() {
        Date oldD = new Date(0), newD = new Date(1000);
        Patient p = new Patient(); p.setPatientId(1);

        Encounter low = new Encounter();
        low.setEncounterId(10);
        low.setPatient(p);
        low.setEncounterType(new EncounterType());
        when(dao.getSavedEncounterDatetime(low)).thenReturn(oldD);
        low.setEncounterDatetime(newD);

        Obs oLow = new Obs(); oLow.setId(30);
        oLow.setObsDatetime(new Date(500));
        low.addObs(oLow);
        when(dao.saveEncounter(low)).thenReturn(low);

        Encounter rLow = service.saveEncounter(low);
        assertEquals(new Date(500), oLow.getObsDatetime());

        Encounter high = new Encounter();
        high.setEncounterId(11);
        high.setPatient(p);
        high.setEncounterType(new EncounterType());
        when(dao.getSavedEncounterDatetime(high)).thenReturn(oldD);
        high.setEncounterDatetime(newD);

        Obs oHigh = new Obs(); oHigh.setId(31);
        oHigh.setObsDatetime(oldD);
        high.addObs(oHigh);
        when(dao.saveEncounter(high)).thenReturn(high);

        Encounter rHigh = service.saveEncounter(high);
        assertEquals(newD, oHigh.getObsDatetime());
    }
}
