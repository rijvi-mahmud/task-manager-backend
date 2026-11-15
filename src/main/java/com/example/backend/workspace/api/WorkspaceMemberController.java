package com.example.backend.workspace.api;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.example.backend.common.api.ApiVersions;
import com.example.backend.identity.security.AuthenticatedUser;
import com.example.backend.workspace.command.AddMemberCommand;
import com.example.backend.workspace.command.RemoveMemberCommand;
import com.example.backend.workspace.command.WorkspaceCommandService;
import com.example.backend.workspace.query.WorkspaceMemberView;
import com.example.backend.workspace.query.WorkspaceQueryService;

/** Membership endpoints. The OpenAPI contract is documented on {@link WorkspaceMemberApi}. */
@RestController
@RequestMapping(path = ApiVersions.BASE + "/workspaces/{workspaceId}/members", version = ApiVersions.V1)
public class WorkspaceMemberController implements WorkspaceMemberApi {

    private final WorkspaceCommandService commands;
    private final WorkspaceQueryService queries;

    public WorkspaceMemberController(WorkspaceCommandService commands, WorkspaceQueryService queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @Override
    @GetMapping
    public List<WorkspaceMemberView> list(@PathVariable Long workspaceId, @AuthenticationPrincipal AuthenticatedUser user) {
        return queries.findMembers(workspaceId, user.id());
    }

    @Override
    @PostMapping
    public ResponseEntity<WorkspaceMemberView> add(@PathVariable Long workspaceId, @RequestBody AddMemberRequest body,
                                                   @AuthenticationPrincipal AuthenticatedUser user) {
        Long memberId = commands.handle(new AddMemberCommand(workspaceId, user.id(), body.email()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}").buildAndExpand(memberId).toUri();
        return ResponseEntity.created(location).body(queries.findMember(workspaceId, memberId, user.id()));
    }

    @Override
    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long workspaceId, @PathVariable Long userId,
                       @AuthenticationPrincipal AuthenticatedUser user) {
        commands.handle(new RemoveMemberCommand(workspaceId, user.id(), userId));
    }
}
