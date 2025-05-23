package teammates.sqlui.webapi;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static teammates.common.datatransfer.InstructorPermissionRole.getEnum;
import static teammates.common.util.Const.InstructorPermissionRoleNames.INSTRUCTOR_PERMISSION_ROLE_CUSTOM;

import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import teammates.common.datatransfer.InstructorPrivileges;
import teammates.common.exception.EntityAlreadyExistsException;
import teammates.common.exception.InvalidParametersException;
import teammates.common.util.Const;
import teammates.common.util.TaskWrapper;
import teammates.storage.sqlentity.Course;
import teammates.storage.sqlentity.Instructor;
import teammates.storage.sqlentity.Student;
import teammates.ui.output.InstructorData;
import teammates.ui.request.InstructorCreateRequest;
import teammates.ui.webapi.CreateInstructorAction;
import teammates.ui.webapi.InvalidOperationException;
import teammates.ui.webapi.JsonResult;

/**
 * SUT: {@link CreateInstructorAction}.
 */
public class CreateInstructorActionTest extends BaseActionTest<CreateInstructorAction> {

    private Instructor typicalInstructor;
    private Course typicalCourse;

    @Override
    String getActionUri() {
        return Const.ResourceURIs.INSTRUCTOR;
    }

    @Override
    String getRequestMethod() {
        return POST;
    }

    @BeforeMethod
    void setUpMethod() {
        Mockito.reset(mockLogic);

        typicalInstructor = getTypicalInstructor();
        typicalCourse = getTypicalCourse();
    }

    @Test
    void testExecute_typicalCase_success() throws Exception {
        String[] params = new String[] {
                Const.ParamsNames.COURSE_ID, typicalCourse.getId(),
        };

        String newInstructorName = "New Instructor";
        String newInstructorEmail = "newinstructor@teammates.tmt";
        String newInstructorRole = Const.InstructorPermissionRoleNames.INSTRUCTOR_PERMISSION_ROLE_COOWNER;
        Instructor newInstructor = new Instructor(typicalCourse, newInstructorName, newInstructorEmail,
                false, null, getEnum(newInstructorRole),
                new InstructorPrivileges(newInstructorRole));

        InstructorCreateRequest requestBody = new InstructorCreateRequest(typicalInstructor.getGoogleId(),
                newInstructorName, newInstructorEmail, newInstructorRole,
                null, false);

        when(mockLogic.getCourse(typicalCourse.getId())).thenReturn(typicalCourse);
        when(mockLogic.createInstructor(any(Instructor.class))).thenReturn(newInstructor);

        loginAsInstructor(typicalInstructor.getGoogleId());

        CreateInstructorAction action = getAction(requestBody, params);
        JsonResult r = getJsonResult(action);
        InstructorData response = (InstructorData) r.getOutput();

        verify(mockLogic, times(1)).getCourse(typicalCourse.getId());
        verify(mockLogic, times(1)).createInstructor(any(Instructor.class));

        verifySpecifiedTasksAdded(Const.TaskQueue.INSTRUCTOR_COURSE_JOIN_EMAIL_QUEUE_NAME, 1);
        verifySpecifiedTasksAdded(Const.TaskQueue.SEARCH_INDEXING_QUEUE_NAME, 1);

        TaskWrapper taskAdded = mockTaskQueuer.getTasksAdded().get(0);
        assertEquals(typicalCourse.getId(), taskAdded.getParamMap().get(Const.ParamsNames.COURSE_ID));
        assertEquals(newInstructor.getEmail(), taskAdded.getParamMap().get(Const.ParamsNames.INSTRUCTOR_EMAIL));

        assertEquals(newInstructor.getName(), response.getName());
        assertEquals(newInstructor.getEmail(), response.getEmail());
    }

    @Test
    void testExecute_existingInstructor_throwsInvalidOperationException() throws Exception {
        String[] params = new String[] {
                Const.ParamsNames.COURSE_ID, typicalCourse.getId(),
        };

        String existingInstructorName = "instructor-name";
        String existingInstructorEmail = "valid@teammates.tmt";
        String existingInstructorRole = Const.InstructorPermissionRoleNames.INSTRUCTOR_PERMISSION_ROLE_COOWNER;

        InstructorCreateRequest requestBody = new InstructorCreateRequest(typicalInstructor.getGoogleId(),
                existingInstructorName, existingInstructorEmail, existingInstructorRole,
                null, false);

        when(mockLogic.getCourse(typicalCourse.getId())).thenReturn(typicalCourse);
        when(mockLogic.createInstructor(any(Instructor.class))).thenThrow(EntityAlreadyExistsException.class);

        loginAsInstructor(typicalInstructor.getGoogleId());

        InvalidOperationException ioe = verifyInvalidOperation(requestBody, params);
        assertEquals("An instructor with the same email address already exists in the course.",
                ioe.getMessage());

        verifyNoTasksAdded();

        verify(mockLogic, times(1)).getCourse(typicalCourse.getId());
        verify(mockLogic, times(1)).createInstructor(any(Instructor.class));
    }

    @Test
    void testExecute_invalidInstructorEmail_throwsInvalidHttpRequestBodyException() throws Exception {
        String[] params = new String[] {
                Const.ParamsNames.COURSE_ID, typicalCourse.getId(),
        };

        String newInstructorName = "New Instructor";
        String invalidInstructorEmail = "newInvalidInstructor.email.tmt";
        String newInstructorRole = Const.InstructorPermissionRoleNames.INSTRUCTOR_PERMISSION_ROLE_COOWNER;

        InstructorCreateRequest requestBody = new InstructorCreateRequest(typicalInstructor.getGoogleId(),
                newInstructorName, invalidInstructorEmail, newInstructorRole,
                null, false);

        when(mockLogic.getCourse(typicalCourse.getId())).thenReturn(typicalCourse);
        when(mockLogic.createInstructor(any(Instructor.class))).thenThrow(InvalidParametersException.class);

        loginAsInstructor(typicalInstructor.getGoogleId());

        verifyHttpRequestBodyFailure(requestBody, params);

        verifyNoTasksAdded();

        verify(mockLogic, times(1)).getCourse(typicalCourse.getId());
        verify(mockLogic, times(1)).createInstructor(any(Instructor.class));
    }

    @Test
    void testExecute_adminToMasqueradeAsInstructor_success() throws Exception {
        String[] params = new String[] {
                Const.ParamsNames.COURSE_ID, typicalCourse.getId(),
        };

        String newInstructorName = "New Instructor";
        String newInstructorEmail = "newinstructor@teammates.tmt";
        String newInstructorRole = Const.InstructorPermissionRoleNames.INSTRUCTOR_PERMISSION_ROLE_COOWNER;
        Instructor newInstructor = new Instructor(typicalCourse, newInstructorName, newInstructorEmail,
                false, null, getEnum(newInstructorRole),
                new InstructorPrivileges(newInstructorRole));

        InstructorCreateRequest requestBody = new InstructorCreateRequest(typicalInstructor.getGoogleId(),
                newInstructorName, newInstructorEmail, newInstructorRole,
                null, false);

        when(mockLogic.getCourse(typicalCourse.getId())).thenReturn(typicalCourse);
        when(mockLogic.createInstructor(any(Instructor.class))).thenReturn(newInstructor);

        loginAsAdmin();

        CreateInstructorAction action = getAction(requestBody, params);
        JsonResult r = getJsonResult(action);
        InstructorData response = (InstructorData) r.getOutput();

        verify(mockLogic, times(1)).getCourse(typicalCourse.getId());
        verify(mockLogic, times(1)).createInstructor(any(Instructor.class));

        verifySpecifiedTasksAdded(Const.TaskQueue.INSTRUCTOR_COURSE_JOIN_EMAIL_QUEUE_NAME, 1);
        verifySpecifiedTasksAdded(Const.TaskQueue.SEARCH_INDEXING_QUEUE_NAME, 1);

        TaskWrapper taskAdded = mockTaskQueuer.getTasksAdded().get(0);
        assertEquals(typicalCourse.getId(), taskAdded.getParamMap().get(Const.ParamsNames.COURSE_ID));
        assertEquals(newInstructor.getEmail(), taskAdded.getParamMap().get(Const.ParamsNames.INSTRUCTOR_EMAIL));

        assertEquals(newInstructor.getName(), response.getName());
        assertEquals(newInstructor.getEmail(), response.getEmail());
    }

    @Test
    void testAccessControl_noLogin_cannotAccess() {
        String[] params = new String[] {
                Const.ParamsNames.COURSE_ID, typicalCourse.getId(),
        };

        logoutUser();
        verifyCannotAccess(params);

        verify(mockLogic, never()).getCourse(typicalCourse.getId());
        verify(mockLogic, never()).getInstructorByGoogleId(any(String.class), any(String.class));
    }

    @Test
    void testAccessControl_unregisteredUsers_cannotAccess() {
        String[] params = new String[] {
                Const.ParamsNames.COURSE_ID, typicalCourse.getId(),
        };

        loginAsUnregistered("unregistered user");
        verifyCannotAccess(params);

        verify(mockLogic, never()).getCourse(typicalCourse.getId());
        verify(mockLogic, never()).getInstructorByGoogleId(any(String.class), any(String.class));
    }

    @Test
    void testAccessControl_students_cannotAccess() {
        Student typicalStudent = getTypicalStudent();
        String[] params = new String[] {
                Const.ParamsNames.COURSE_ID, typicalCourse.getId(),
        };

        loginAsStudent(typicalStudent.getGoogleId());
        verifyCannotAccess(params);

        verify(mockLogic, never()).getCourse(typicalCourse.getId());
        verify(mockLogic, never()).getInstructorByGoogleId(any(String.class), any(String.class));
    }

    @Test
    void testAccessControl_instructorsFromDifferentCourse_cannotAccess() {
        Course differentCourse = new Course("different id", "different name", Const.DEFAULT_TIME_ZONE,
                "teammates");
        Instructor instructorFromDifferentCourse = getTypicalInstructor();
        instructorFromDifferentCourse.setCourse(differentCourse);
        instructorFromDifferentCourse.setGoogleId("different google id");

        String[] params = new String[] {
                Const.ParamsNames.COURSE_ID, typicalCourse.getId(),
        };

        when(mockLogic.getCourse(typicalCourse.getId())).thenReturn(typicalCourse);
        when(mockLogic.getInstructorByGoogleId(typicalCourse.getId(), instructorFromDifferentCourse.getGoogleId()))
                .thenReturn(instructorFromDifferentCourse);

        loginAsInstructor(instructorFromDifferentCourse.getGoogleId());

        verifyCannotAccess(params);

        verify(mockLogic, times(1)).getCourse(typicalCourse.getId());
        verify(mockLogic, times(1))
                .getInstructorByGoogleId(typicalCourse.getId(), instructorFromDifferentCourse.getGoogleId());
    }

    @Test
    void testAccessControl_instructorWithoutCorrectCoursePrivilege_cannotAccess() {
        Instructor instructorWithoutCorrectPrivilege = getTypicalInstructor();
        instructorWithoutCorrectPrivilege.setGoogleId("no privilege");
        instructorWithoutCorrectPrivilege.setEmail("helper@teammates.tmt");
        instructorWithoutCorrectPrivilege.setPrivileges(new InstructorPrivileges(INSTRUCTOR_PERMISSION_ROLE_CUSTOM));

        String[] params = new String[] {
                Const.ParamsNames.COURSE_ID, typicalCourse.getId(),
        };

        when(mockLogic.getCourse(typicalCourse.getId())).thenReturn(typicalCourse);
        when(mockLogic.getInstructorByGoogleId(typicalCourse.getId(), instructorWithoutCorrectPrivilege.getGoogleId()))
                .thenReturn(instructorWithoutCorrectPrivilege);

        loginAsInstructor(instructorWithoutCorrectPrivilege.getGoogleId());

        verifyCannotAccess(params);

        verify(mockLogic, times(1)).getCourse(typicalCourse.getId());
        verify(mockLogic, times(1))
                .getInstructorByGoogleId(typicalCourse.getId(), instructorWithoutCorrectPrivilege.getGoogleId());
    }

    @Test
    void testAccessControl_adminToMasqueradeAsInstructor_canAccess() {
        String[] params = new String[] {
                Const.ParamsNames.COURSE_ID, typicalCourse.getId(),
        };

        loginAsAdmin();

        verifyCanMasquerade(typicalInstructor.getGoogleId(), params);

        verify(mockLogic, never()).getCourse(typicalCourse.getId());
        verify(mockLogic, never()).getInstructorByGoogleId(typicalCourse.getId(), typicalInstructor.getGoogleId());
    }

    @Test
    void testAccessControl_instructorWithCorrectCoursePrivilege_canAccess() {
        String[] params = new String[] {
                Const.ParamsNames.COURSE_ID, typicalCourse.getId(),
        };

        when(mockLogic.getCourse(typicalCourse.getId())).thenReturn(typicalCourse);
        when(mockLogic.getInstructorByGoogleId(typicalCourse.getId(), typicalInstructor.getGoogleId()))
                .thenReturn(typicalInstructor);

        loginAsInstructor(typicalInstructor.getGoogleId());

        verifyCanAccess(params);

        verify(mockLogic, times(1)).getCourse(typicalCourse.getId());
        verify(mockLogic, times(1))
                .getInstructorByGoogleId(typicalCourse.getId(), typicalInstructor.getGoogleId());
    }

}
