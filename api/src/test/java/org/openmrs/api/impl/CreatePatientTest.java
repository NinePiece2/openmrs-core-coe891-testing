package org.openmrs.api.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.api.db.PatientDAO;
import org.openmrs.test.jupiter.BaseContextMockTest;

import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

public class CreatePatientTest extends BaseContextMockTest {

    private PatientServiceImpl spyService;
    private PatientDAO patientDaoMock;

    @BeforeEach
    public void setUp() {
        patientDaoMock = mock(PatientDAO.class);

        // Spy on PatientServiceImpl and inject DAO
        spyService = spy(new PatientServiceImpl());
        spyService.setPatientDAO(patientDaoMock);

        // Bypass internal logic that causes validation or privilege issues
        lenient().doNothing().when(spyService).requireAppropriatePatientModificationPrivilege(any());
        lenient().doNothing().when(spyService).checkPatientIdentifiers(any());
        lenient().doNothing().when(spyService).setPreferredPatientIdentifier(any());
        lenient().doNothing().when(spyService).setPreferredPatientName(any());
        lenient().doNothing().when(spyService).setPreferredPatientAddress(any());
    }

    @Test
    public void savePatient_shouldReturnPatientWhenValid() {
        // Mock patient
        Patient mockPatient = mock(Patient.class);

        // Stub DAO return
        when(patientDaoMock.savePatient(any(Patient.class))).thenReturn(mockPatient);

        // When
        Patient result = spyService.savePatient(mockPatient);

        // Then
        assertNotNull(result);
        verify(patientDaoMock).savePatient(mockPatient);
    }
    
    @Test
    public void savePatient_shouldThrowExceptionIfPatientIsNull() {
        assertThrows(NullPointerException.class, () -> spyService.savePatient(null));
    }
    
    @Test
    public void savePatient_shouldThrowExceptionWhenIdentifiersMissing() {
        Patient patient = new Patient(); // No identifiers

        PatientServiceImpl realService = new PatientServiceImpl();
        realService.setPatientDAO(patientDaoMock);

        assertThrows(Exception.class, () -> realService.savePatient(patient));
    }

    @Test
    public void savePatient_shouldUpdateExistingPatient() {
        // Given: a patient with an ID (i.e., existing in system)
        Patient mockPatient = mock(Patient.class);
        lenient().when(mockPatient.getPatientId()).thenReturn(456);
        when(patientDaoMock.savePatient(any(Patient.class))).thenReturn(mockPatient);

        // When
        Patient result = spyService.savePatient(mockPatient);

        // Then
        assertNotNull(result);
        verify(patientDaoMock).savePatient(mockPatient);
    }

    @Test
    public void purgePatient_shouldCallDaoDeletePatient() {
        Patient mockPatient = mock(Patient.class);

        spyService.purgePatient(mockPatient);

        verify(patientDaoMock).deletePatient(mockPatient);
    }


    @Test
    public void deletePatient_shouldNotThrowWhenPatientIsNull() {
        assertDoesNotThrow(() -> spyService.purgePatient(null));
    }

    
    @Test
    public void deletePatient_shouldNotRevoidAlreadyVoidedPatient() {
        Patient alreadyVoided = mock(Patient.class);
        lenient().when(alreadyVoided.getVoided()).thenReturn(true);

        spyService.purgePatient(alreadyVoided);

        // Should still call DAO, but not change fields again
        verify(alreadyVoided, never()).setVoided(true);
    }

//CFG BASED TESTING
    
    @Test
    public void savePatient_shouldSetPreferredAndCheckIdentifiers_whenNotVoidedAndOneIdentifier() {
        Patient patient = mock(Patient.class);
        PatientIdentifier identifier = mock(PatientIdentifier.class);

        when(patient.getVoided()).thenReturn(false);
        when(patient.getIdentifiers()).thenReturn(Collections.singleton(identifier));
        when(patient.getPatientIdentifier()).thenReturn(identifier);
        when(patientDaoMock.savePatient(patient)).thenReturn(patient);

        Patient result = spyService.savePatient(patient);

        assertNotNull(result);
        verify(identifier).setPreferred(true); // covers edge: 2 → 3
        verify(spyService).checkPatientIdentifiers(patient); // covers edge: 4 → 5
    }

    @Test
    public void savePatient_shouldOnlyCheckIdentifiers_whenNotVoidedAndMultipleIdentifiers() {
        Patient patient = mock(Patient.class);
        PatientIdentifier identifier1 = mock(PatientIdentifier.class);
        PatientIdentifier identifier2 = mock(PatientIdentifier.class);

        when(patient.getVoided()).thenReturn(false);
        when(patient.getIdentifiers()).thenReturn(Set.of(identifier1, identifier2));
        when(patientDaoMock.savePatient(patient)).thenReturn(patient);

        Patient result = spyService.savePatient(patient);

        assertNotNull(result);
        verify(identifier1, never()).setPreferred(true); // ensures first if fails
        verify(identifier2, never()).setPreferred(true);
        verify(spyService).checkPatientIdentifiers(patient); // second if still true
    }

    @Test
    public void savePatient_shouldSkipAllConditionals_whenVoidedIsTrue() {
        Patient patient = mock(Patient.class);
        PatientIdentifier identifier = mock(PatientIdentifier.class);

        when(patient.getVoided()).thenReturn(true);
        lenient().when(patient.getIdentifiers()).thenReturn(Collections.singleton(identifier));
        when(patientDaoMock.savePatient(patient)).thenReturn(patient);

        Patient result = spyService.savePatient(patient);

        assertNotNull(result);
        verify(identifier, never()).setPreferred(true); // first if false
        verify(spyService, never()).checkPatientIdentifiers(patient); // second if false
    }
}

