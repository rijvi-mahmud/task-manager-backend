package com.example.backend.workspace.api;

import java.util.List;

import org.springframework.http.ResponseEntity;

import com.example.backend.common.api.ApiTags;
import com.example.backend.common.api.ProblemResponses;
import com.example.backend.identity.security.AuthenticatedUser;
import com.example.backend.workspace.query.WorkspaceMemberView;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** OpenAPI contract for workspace membership, implemented by {@link WorkspaceMemberController}. */
@Tag(name = ApiTags.MEMBERS)
public interface WorkspaceMemberApi {

    @Operation(operationId = "listMembers", summary = "List workspace members",
            description = "Sorted by name. These are the people tasks can be assigned to.")
    @ApiResponse(responseCode = "200", description = "All members")
    @ApiResponse(responseCode = "404", ref = ProblemResponses.NOT_FOUND_REF)
    List<WorkspaceMemberView> list(@Parameter(description = "Workspace id") Long workspaceId,
                                   @Parameter(hidden = true) AuthenticatedUser user);

    @Operation(operationId = "addMember", summary = "Add a member by email",
            description = "Owners only. If no account uses the email yet, a pending account is created; "
                    + "the person joins by registering with that email.")
    @ApiResponse(responseCode = "201", description = "Member added",
            headers = @Header(name = "Location", description = "URI of the new member",
                    schema = @Schema(type = "string", format = "uri")),
            content = @Content(schema = @Schema(implementation = WorkspaceMemberView.class)))
    @ApiResponse(responseCode = "400", ref = ProblemResponses.BAD_REQUEST_REF)
    @ApiResponse(responseCode = "404", ref = ProblemResponses.NOT_FOUND_REF)
    @ApiResponse(responseCode = "409", ref = ProblemResponses.CONFLICT_REF)
    ResponseEntity<WorkspaceMemberView> add(@Parameter(description = "Workspace id") Long workspaceId,
                                            @Valid AddMemberRequest body, @Parameter(hidden = true) AuthenticatedUser user);

    @Operation(operationId = "removeMember", summary = "Remove a member, or leave",
            description = "Owners can remove anyone; anyone can remove themselves. The member's tasks in this "
                    + "workspace become unassigned. The last owner cannot leave (`409`); delete the workspace instead.")
    @ApiResponse(responseCode = "204", description = "Member removed")
    @ApiResponse(responseCode = "404", ref = ProblemResponses.NOT_FOUND_REF)
    @ApiResponse(responseCode = "409", ref = ProblemResponses.CONFLICT_REF)
    void remove(@Parameter(description = "Workspace id") Long workspaceId,
                @Parameter(description = "User id of the member") Long userId,
                @Parameter(hidden = true) AuthenticatedUser user);
}
