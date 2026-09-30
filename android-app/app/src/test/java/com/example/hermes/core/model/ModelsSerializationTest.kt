package com.example.hermes.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ModelsSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Test
    fun testAuthStatusResponseDeserialization() {
        val jsonStr = """{"auth_enabled": true, "logged_in": false}"""
        val response = json.decodeFromString<AuthStatusResponse>(jsonStr)
        assertTrue(response.authEnabled)
        assertFalse(response.loggedIn)
    }

    @Test
    fun testProfilesResponseDeserialization() {
        val jsonStr = """
            {
              "profiles": [
                {
                  "name": "default",
                  "is_default": true,
                  "is_active": true,
                  "model": "anthropic/claude-3-5-sonnet",
                  "provider": "openrouter",
                  "skill_count": 8
                },
                {
                  "name": "mario",
                  "is_default": false,
                  "is_active": false,
                  "model": "openai/gpt-4o",
                  "provider": "openai",
                  "skill_count": 3
                }
              ],
              "active": "default"
            }
        """.trimIndent()
        val response = json.decodeFromString<ProfilesResponse>(jsonStr)
        assertEquals(2, response.profiles.size)
        assertEquals("default", response.active)
        assertEquals("mario", response.profiles[1].name)
        assertEquals("openai/gpt-4o", response.profiles[1].model)
    }

    @Test
    fun testSessionDetailResponseDeserialization() {
        val jsonStr = """
            {
              "session": {
                "session_id": "sid_12345",
                "title": "Fix memory leak",
                "workspace": "/tmp/test",
                "model": "claude-3-5-sonnet",
                "created_at": 1727500000.0,
                "updated_at": 1727504000.0,
                "pinned": true,
                "archived": false,
                "messages": [
                  {
                    "role": "user",
                    "content": "Can you check main.py?",
                    "timestamp": 1727500100
                  },
                  {
                    "role": "assistant",
                    "content": "Checking main.py...",
                    "timestamp": 1727500105,
                    "tool_calls": [
                      {
                        "name": "read_file",
                        "output": "import os\n...",
                        "duration": 0.42,
                        "is_error": false
                      }
                    ]
                  }
                ]
              }
            }
        """.trimIndent()
        val response = json.decodeFromString<SessionDetailResponse>(jsonStr)
        assertEquals("sid_12345", response.session.sessionId)
        assertEquals(2, response.session.messages.size)
        assertEquals("user", response.session.messages[0].role)
        assertEquals("assistant", response.session.messages[1].role)
        assertEquals(1, response.session.messages[1].toolCalls?.size)
        assertEquals("read_file", response.session.messages[1].toolCalls?.first()?.name)
        assertEquals(0.42, response.session.messages[1].toolCalls?.first()?.duration ?: 0.0, 0.001)
    }

    @Test
    fun testSsePayloadsDeserialization() {
        // Token
        val tokenJson = """{"text": "Hello, world!"}"""
        val token = json.decodeFromString<TokenPayload>(tokenJson)
        assertEquals("Hello, world!", token.text)

        // Reasoning
        val reasoningJson = """{"text": "Evaluating algorithm efficiency"}"""
        val reasoning = json.decodeFromString<ReasoningPayload>(reasoningJson)
        assertEquals("Evaluating algorithm efficiency", reasoning.text)

        // Tool started
        val toolJson = """{"event_type": "tool.started", "name": "execute_command", "preview": "ls -la"}"""
        val tool = json.decodeFromString<ToolEventPayload>(toolJson)
        assertEquals("execute_command", tool.name)
        assertEquals("ls -la", tool.preview)

        // Tool completed
        val toolCompleteJson = """{"event_type": "tool.completed", "name": "execute_command", "preview": "total 4", "duration": 0.15, "is_error": false}"""
        val toolComplete = json.decodeFromString<ToolCompletePayload>(toolCompleteJson)
        assertEquals("execute_command", toolComplete.name)
        assertEquals(0.15, toolComplete.duration ?: 0.0, 0.001)
        assertFalse(toolComplete.isError)

        // Approval
        val approvalJson = """{"id": "appr_1", "tool": "execute_command", "command": "rm -rf test"}"""
        val approval = json.decodeFromString<ApprovalPayload>(approvalJson)
        assertEquals("appr_1", approval.id)
        assertEquals("rm -rf test", approval.command)

        // Clarify
        val clarifyJson = """{"question": "Which branch?", "options": ["main", "dev"]}"""
        val clarify = json.decodeFromString<ClarifyPayload>(clarifyJson)
        assertEquals("Which branch?", clarify.question)
        assertEquals(listOf("main", "dev"), clarify.options)

        // Metering
        val meterJson = """{"session_id": "sid_1", "tokens_in": 100, "tokens_out": 50, "cost_usd": 0.002}"""
        val meter = json.decodeFromString<MeteringPayload>(meterJson)
        assertEquals(100L, meter.tokensIn)
        assertEquals(0.002, meter.costUsd ?: 0.0, 0.0001)
    }

    @Test
    fun testHealthResponseDeserialization() {
        val healthJson = """{"status": "ok", "sessions": 5, "active_streams": 1, "active_runs": 1, "uptime_seconds": 3600.5}"""
        val health = json.decodeFromString<HealthResponse>(healthJson)
        assertEquals("ok", health.status)
        assertEquals(5, health.sessions)
        assertEquals(1, health.activeStreams)
        assertEquals(3600.5, health.uptimeSeconds, 0.1)
    }

    @Test
    fun testNewSessionResponseDeserialization() {
        val nestedJson = """{"session": {"session_id": "sid_new_999", "title": "New Chat", "created_at": 1727500000.0, "updated_at": 1727500000.0, "messages": []}}"""
        val res1 = json.decodeFromString<NewSessionResponse>(nestedJson)
        assertEquals("sid_new_999", res1.sessionId)

        val directJson = """{"session_id": "sid_direct_888"}"""
        val res2 = json.decodeFromString<NewSessionResponse>(directJson)
        assertEquals("sid_direct_888", res2.sessionId)
    }

    @Test
    fun testSkillsDeserialization() {
        val skillsJson = """
            {
              "skills": [
                {"name": "git", "description": "Git version control operations", "category": "vcs"},
                {"name": "curl", "description": "HTTP request tool", "category": "network"}
              ],
              "categories": ["vcs", "network"],
              "count": 2
            }
        """.trimIndent()
        val res = json.decodeFromString<SkillsResponse>(skillsJson)
        assertEquals(2, res.skills.size)
        assertEquals("git", res.skills[0].name)
        assertEquals("vcs", res.skills[0].category)

        val detailJson = """
            {
              "name": "git",
              "description": "Git operations",
              "content": "# Git Skill\n\nRun git commands.",
              "path": "/skills/git/SKILL.md"
            }
        """.trimIndent()
        val detail = json.decodeFromString<SkillDetailResponse>(detailJson)
        assertEquals("git", detail.name)
        assertTrue(detail.content.contains("# Git Skill"))
    }

    @Test
    fun testMemoryDeserialization() {
        val memoryJson = """
            {
              "memory": "User prefers Kotlin over Java.",
              "user": "Name: Alice\nRole: Lead Engineer",
              "soul": "You are Hermes, a helpful autonomous agent.",
              "memory_path": "/home/.hermes/memories/MEMORY.md"
            }
        """.trimIndent()
        val res = json.decodeFromString<MemoryResponse>(memoryJson)
        assertEquals("User prefers Kotlin over Java.", res.memory)
        assertTrue(res.user.contains("Alice"))
        assertTrue(res.soul.contains("Hermes"))
    }

    @Test
    fun testWorkspacesDeserialization() {
        val wsJson = """
            {
              "workspaces": [
                {"name": "Home", "path": "/workspace", "default": true},
                {"name": "Project Alpha", "path": "/workspace/alpha", "default": false}
              ],
              "last": "/workspace"
            }
        """.trimIndent()
        val res = json.decodeFromString<WorkspacesResponse>(wsJson)
        assertEquals(2, res.workspaces.size)
        assertEquals("Home", res.workspaces[0].name)
        assertEquals("/workspace", res.last)
    }

    @Test
    fun testYoloStatusResponseDeserialization() {
        val yoloJson = """{"yolo_enabled": true}"""
        val res = json.decodeFromString<YoloStatusResponse>(yoloJson)
        assertTrue(res.yoloEnabled)

        val yoloToggleRes = """{"ok": true, "yolo_enabled": false}"""
        val res2 = json.decodeFromString<YoloStatusResponse>(yoloToggleRes)
        assertFalse(res2.yoloEnabled)
        assertEquals(true, res2.ok)
    }

    @Test
    fun testYoloToggleRequestSerialization() {
        val req = YoloToggleRequest(sessionId = "sess_xyz", enabled = true)
        val serialized = json.encodeToString(YoloToggleRequest.serializer(), req)
        assertTrue(serialized.contains("sess_xyz"))
        assertTrue(serialized.contains("true"))
    }

    @Test
    fun testKanbanBoardDeserialization() {
        val kanbanJson = """
            {
              "columns": [
                {
                  "name": "todo",
                  "tasks": [
                    {
                      "id": "task_abc",
                      "title": "Implement feature X",
                      "body": "Need to finish API and UI",
                      "status": "todo",
                      "priority": 1,
                      "assignee": "john",
                      "age_seconds": 120.5,
                      "comment_count": 2
                    }
                  ]
                },
                {
                  "name": "done",
                  "tasks": []
                }
              ],
              "tenants": ["default"],
              "assignees": ["john", "mario"],
              "latest_event_id": 42,
              "changed": true,
              "read_only": false
            }
        """.trimIndent()
        val board = json.decodeFromString<KanbanBoardResponse>(kanbanJson)
        assertEquals(2, board.columns.size)
        assertEquals("todo", board.columns[0].name)
        assertEquals(1, board.columns[0].tasks.size)
        val task = board.columns[0].tasks[0]
        assertEquals("task_abc", task.id)
        assertEquals("Implement feature X", task.title)
        assertEquals("john", task.assignee)
        assertEquals(1, task.priority)
        assertEquals(42, board.latestEventId)
    }

    @Test
    fun testKanbanBoardsListDeserialization() {
        val boardsJson = """
            {
              "boards": [
                {
                  "slug": "default",
                  "name": "Main Board",
                  "is_current": true,
                  "total": 12
                },
                {
                  "slug": "experiments",
                  "name": "Experiments",
                  "is_current": false,
                  "total": 3
                }
              ],
              "current": "default",
              "read_only": false
            }
        """.trimIndent()
        val res = json.decodeFromString<KanbanBoardsResponse>(boardsJson)
        assertEquals(2, res.boards.size)
        assertEquals("default", res.current)
        assertEquals("Main Board", res.boards[0].name)
        assertTrue(res.boards[0].isCurrent)
        assertEquals(12, res.boards[0].total)
    }

    @Test
    fun testKanbanBoardWithObjectAgeSecondsAndNulls() {
        val kanbanJson = """
            {
              "columns": [
                {
                  "name": "done",
                  "tasks": [
                    {
                      "id": "task_xyz",
                      "title": "Complex task with object age",
                      "body": null,
                      "status": "done",
                      "priority": 0,
                      "assignee": null,
                      "tenant": null,
                      "progress": null,
                      "age_seconds": {
                        "created_age_sec": 7200.0,
                        "updated_age_sec": 120.0
                      },
                      "link_counts": null,
                      "comment_count": 0
                    }
                  ]
                }
              ],
              "tenants": [],
              "assignees": [],
              "latest_event_id": 99,
              "changed": true,
              "read_only": false
            }
        """.trimIndent()
        val board = json.decodeFromString<KanbanBoardResponse>(kanbanJson)
        assertEquals(1, board.columns.size)
        val task = board.columns[0].tasks[0]
        assertEquals("task_xyz", task.id)
        assertNotNull(task.ageSeconds)
        assertEquals(7200.0, task.ageSeconds!!, 0.001)
    }

    @Test
    fun testSessionsResponseWithMixedDateFormats() {
        val sessionsJson = """
            {
              "sessions": [
                {
                  "session_id": "sid_1",
                  "title": "Chat 1",
                  "created_at": "2026-09-30T07:12:00Z",
                  "updated_at": "2026-09-30 08:30:00",
                  "last_message_at": 1727500000.0,
                  "message_count": 5
                },
                {
                  "session_id": "sid_2",
                  "title": "Chat 2",
                  "created_at": 1727400000,
                  "updated_at": 1727450000.5,
                  "last_message_at": "2026-09-29T10:00:00Z",
                  "message_count": 2
                }
              ],
              "other_profile_count": 1
            }
        """.trimIndent()
        val res = json.decodeFromString<SessionsResponse>(sessionsJson)
        assertEquals(2, res.sessions.size)
        assertTrue(res.sessions[0].createdAt > 0)
        assertTrue(res.sessions[0].updatedAt > 0)
        assertEquals(1727500000.0, res.sessions[0].lastMessageAt!!, 0.001)
        assertTrue(res.sessions[1].lastMessageAt!! > 0)
    }

    @Test
    fun testSessionDetailWithComplexToolCallsAndReasoning() {
        val detailJson = """
            {
              "session_id": "sid_complex",
              "title": "Complex Session",
              "created_at": "2026-09-30 07:00:00",
              "updated_at": 1727670000.0,
              "messages": [
                {
                  "role": "assistant",
                  "content": null,
                  "reasoning": {
                    "text": "Thinking about the query..."
                  },
                  "tool_calls": [
                    {
                      "name": "bash",
                      "output": {
                        "status": "success",
                        "code": 0
                      },
                      "duration": {
                        "seconds": 1.25
                      }
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
        val detail = json.decodeFromString<SessionDetail>(detailJson)
        assertEquals("sid_complex", detail.sessionId)
        assertEquals(1, detail.messages.size)
        val msg = detail.messages[0]
        assertEquals("", msg.content)
        assertNotNull(msg.reasoning)
        assertTrue(msg.reasoning!!.contains("Thinking about the query"))
        val tc = msg.toolCalls?.firstOrNull()
        assertNotNull(tc)
        assertEquals("bash", tc?.name)
        assertNotNull(tc?.output)
        assertTrue(tc!!.output!!.contains("success"))
        assertEquals(1.25, tc.duration ?: 0.0, 0.001)
    }
}


