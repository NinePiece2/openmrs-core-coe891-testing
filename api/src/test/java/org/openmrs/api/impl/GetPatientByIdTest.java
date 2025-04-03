package org.openmrs.api.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.openmrs.Patient;
import org.openmrs.api.db.PatientDAO;
import org.openmrs.test.jupiter.BaseContextMockTest;

public class GetPatientByIdTest extends BaseContextMockTest {

    private PatientServiceImpl patientService;

    @Mock
    private PatientDAO patientDaoMock;

	@BeforeEach
    public void setUp() {
        patientService = new PatientServiceImpl();
        patientService.setPatientDAO(patientDaoMock);
    }

    @Test
    public void getPatient_shouldReturnPatientGivenValidId() {
        // Given
        final Integer patientId = 123;
        final Patient expectedPatient = new Patient();
        expectedPatient.setPatientId(patientId);
        expectedPatient.setUuid("some-uuid");

        when(patientDaoMock.getPatient(patientId)).thenReturn(expectedPatient);

        // When
        Patient actualPatient = patientService.getPatient(patientId);

        // Then
        assertNotNull(actualPatient);
        assertEquals(expectedPatient.getPatientId(), actualPatient.getPatientId());
        assertEquals(expectedPatient.getUuid(), actualPatient.getUuid());
    }

    @Test
    public void getPatient_shouldReturnNullGivenNonExistentId() {
        // Given
        final Integer nonExistentId = 999;
        when(patientDaoMock.getPatient(nonExistentId)).thenReturn(null);

        // When
        Patient actualPatient = patientService.getPatient(nonExistentId);

        // Then
        assertNull(actualPatient);
    }
    
    @Test
    public void getPatient_shouldReturnNullGivenNullId() {
    	assertNull(patientService.getPatient(null));
    }
    
    @Test
    public void getPatient_shouldReturnNullGivenNegativeId() {
    	assertNull(patientService.getPatient(-1));
    }
}
