package engine

import engine.core.*
import glm_.max
import glm_.vec2.Vec2
import glm_.vec4.Vec4
import imgui.*
import imgui.classes.InputTextCallbackData
import imgui.classes.TextFilter
import imgui.internal.classes.Rect
import imgui.internal.sections.Axis
import kotlin.reflect.KMutableProperty0


//-------------------------------------------------------------------------
// TEST ENGINE: USER INTERFACE
//-------------------------------------------------------------------------
// - DrawTestLog() [internal]
// - GetVerboseLevelName() [internal]
// - ImGuiTestEngine_ShowTestGroup() [Internal]
// - ImGuiTestEngine_ShowTestWindow()
//-------------------------------------------------------------------------

fun TestEngine.drawTestLog(test: Test, isInteractive: Boolean) {
    val errorCol = COL32(255, 150, 150, 255)
    val warningCol = COL32(240, 240, 150, 255)
    val unimportantCol = COL32(190, 190, 190, 255)

    // FIXME-OPT: Split TestLog by lines so we can clip it easily.
    val log = test.testLog
//    val text = test.testLog.buffer.begin()
//    const char * text_end = test->TestLog.Buffer.end()
//    ImGui::PushStyleVar(ImGuiStyleVar_ItemSpacing, ImVec2(6.0f, 2.0f) * e->IO.DpiScale)
//    ImVector<ImGuiTestLogLineInfo>& line_info_vector = test->Status == ImGuiTestStatus_Error ? log->LineInfoError : log->LineInfo
//    ImGuiListClipper clipper
//            clipper.Begin(line_info_vector.Size)
//    while (clipper.Step()) {
//        for (int line_no = clipper.DisplayStart; line_no < clipper.DisplayEnd; line_no++)
//        {
//            ImGuiTestLogLineInfo& line_info = line_info_vector[line_no]
//            const char * line_start = text +line_info.LineOffset
//            const char * line_end = strchr (line_start, '\n')
//            if (line_end == NULL)
//                line_end = text_end
//
//            switch(line_info.Level)
//            {
//                case ImGuiTestVerboseLevel_Error :
//                ImGui::PushStyleColor(ImGuiCol_Text, error_col)
//                break
//                case ImGuiTestVerboseLevel_Warning :
//                ImGui::PushStyleColor(ImGuiCol_Text, warning_col)
//                break
//                case ImGuiTestVerboseLevel_Debug :
//                case ImGuiTestVerboseLevel_Trace :
//                ImGui::PushStyleColor(ImGuiCol_Text, unimportant_col)
//                break
//                default:
//                ImGui::PushStyleColor(ImGuiCol_Text, IM_COL32_WHITE)
//                break
//            }
//            ImGui::TextUnformatted(line_start, line_end)
//            ImGui::PopStyleColor()
//
//            ImGui::PushID(line_no)
//            if (ImGui::BeginPopupContextItem("Context", 1)) {
//                if (!ParseLineAndDrawFileOpenItemForSourceFile(e, test, line_start, line_end))
//                    ImGui::MenuItem("No options", NULL, false, false)
//                ImGui::EndPopup()
//            }
//            ImGui::PopID()
//        }
//    }
//    ImGui::PopStyleVar()
}

fun helpTooltip(desc: String) {
    if (ImGui.isItemHovered())
        ImGui.setTooltip(desc)
}

fun showTestGroup(e: TestEngine, group: TestGroup, filter: TextFilter) {

    val style = ImGui.style

    //ImGui::Text("TESTS (%d)", engine->TestsAll.Size);
    if (ImGui.button("Run All"))
        e.queueTests(group, filter.inputBuf.cStr) // FIXME: Filter func differs

    ImGui.sameLine()
    filter.draw("##filter", -1f)
    ImGui.separator()

    if (ImGui.beginChild("Tests", Vec2())) {
        ImGui.pushStyleVar(StyleVar.ItemSpacing, Vec2(6, 3) * e.io.dpiScale)
        ImGui.pushStyleVar(StyleVar.FramePadding, Vec2(4, 1) * e.io.dpiScale)
        for (n in e.testsAll.indices) {
            val test = e.testsAll[n]
            if (test.group != group)
                continue
            if (!filter.passFilter(test.name!!) && !filter.passFilter(test.category!!))
                continue

            val testContext = e.testContext!!.takeIf { it.test === test }

            ImGui.pushID(n)

            val statusColor = when (test.status) {
                TestStatus.Error -> Vec4(0.9f, 0.1f, 0.1f, 1f)
                TestStatus.Success -> Vec4(0.1f, 0.9f, 0.1f, 1f)
                TestStatus.Queued, TestStatus.Running -> when {
                    testContext?.runFlags?.has(TestRunFlag.NoTestFunc) == true -> Vec4(0.8f, 0f, 0.8f, 1f)
                    else -> Vec4(0.8f, 0.4f, 0.1f, 1f)
                }
                else -> Vec4(0.4f, 0.4f, 0.4f, 1f)
            }

            val p = Vec2(ImGui.cursorScreenPos)
            ImGui.colorButton("status", statusColor, ColorEditFlag.NoTooltip.i)
            ImGui.sameLine()
            if (test.status == TestStatus.Running)
                ImGui.renderText(p + style.framePadding + Vec2(), "|\\0/\\0-\\0\\".substring((((ImGui.frameCount) / 5) and 3) shl 1))

            var queueTest = false
            var queueGuiFunc = false
            var selectTest = false

            if (ImGui.button("Run")) {
                queueTest = true
                selectTest = true
            }
            ImGui.sameLine()

            val buf = "${test.category ?: ""}${" ".repeat(10 - (test.category?.length ?: 0))} - ${test.name}"
            if (ImGui.selectable(buf, test == e.uiSelectedTest))
                selectTest = true

            // Double-click to run test, CTRL+Double-click to run GUI function
            if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(MouseButton.Left))
                if (ImGui.io.keyCtrl)
                    queueGuiFunc = true
                else
                    queueTest = true

            /*if (ImGui::IsItemHovered() && test->TestLog.size() > 0)
            {
            ImGui::BeginTooltip();
            DrawTestLog(engine, test, false);
            ImGui::EndTooltip();
            }*/

            if (e.uiSelectAndScrollToTest == test)
                ImGui.setScrollHereY()

            var viewSource = false
            if (ImGui.beginPopupContextItem()) {
                selectTest = true

                if (ImGui.menuItem("Run test"))
                    queueTest = true

                val isRunningGuiFunc = testContext?.runFlags?.has(TestRunFlag.NoTestFunc) == true
                if (ImGui.menuItem("Run GUI func", selected = isRunningGuiFunc))
                    if (isRunningGuiFunc)
                        e.abortTest()
                    else queueGuiFunc = true
                ImGui.separator()

                val openSourceAvailable = test.sourceFile != null && e.io.srcFileOpenFunc != null
                if (openSourceAvailable) {
                    TODO()
//                    buf.setf("Open source (%s:%d)", test->SourceFileShort, test->SourceLine)
//                    if (ImGui::MenuItem(buf.c_str()))
//                        { engine ->
//                            IO.SrcFileOpenFunc(test->SourceFile, test->SourceLine, engine->IO.UserData)
//                        }
//                    if (ImGui::MenuItem("View source..."))
//                        viewSource = true
                } else {
                    ImGui.menuItem("Open source", selected = false, enabled = false)
                    ImGui.menuItem("View source", selected = false, enabled = false)
                }

                ImGui.separator()
                if (ImGui.menuItem("Copy name", selected = false))
                    ImGui.clipboardText = test.name!!

                if (ImGui.menuItem("Copy log", selected = false, enabled = test.testLog.buffer.isNotEmpty()))
                    ImGui.clipboardText = test.testLog.buffer.toString()

                if (ImGui.menuItem("Clear log", selected = false, enabled = test.testLog.buffer.isNotEmpty()))
                    test.testLog.clear()

                ImGui.endPopup()
            }

            // Process source popup
//            static ImGuiTextBuffer source_blurb
//            static int goto_line = -1
            if (viewSource) {
                TODO()
//                source_blurb.clear()
//                size_t file_size = 0
//                char * file_data = (char *) ImFileLoadToMemory (test->SourceFile, "rb", &file_size)
//                if (file_data)
//                    source_blurb.append(file_data, file_data + file_size)
//                else
//                    source_blurb.append("<Error loading sources>")
//                goto_line = (test->SourceLine+test->SourceLineEnd) / 2
//                ImGui::OpenPopup("Source")
            }
            if (ImGui.beginPopup("Source")) {
                TODO()
//                // FIXME: Local vs screen pos too messy :(
//                const ImVec2 start_pos = ImGui::GetCursorStartPos()
//                const float line_height = ImGui::GetTextLineHeight()
//                if (goto_line != -1)
//                    ImGui::SetScrollFromPosY(start_pos.y + (goto_line - 1) * line_height, 0.5f)
//                goto_line = -1
//
//                ImRect r (0.0f, test->SourceLine * line_height, ImGui::GetWindowWidth(), (test->SourceLine+1) * line_height) // SourceLineEnd is too flaky
//                ImGui::GetWindowDrawList()->AddRectFilled(ImGui::GetWindowPos()+start_pos+r.Min, ImGui::GetWindowPos()+start_pos+r.Max, IM_COL32(80, 80, 150, 150))
//
//                ImGui::TextUnformatted(source_blurb.c_str(), source_blurb.end())
//                ImGui::EndPopup()
            }

            // Process selection
            if (selectTest)
                e.uiSelectedTest = test

            // Process queuing
            if (!e.io.runningTests)
                if (queueTest)
                    e.queueTest(test, TestRunFlag.ManualRun.i)
                else if (queueGuiFunc)
                    e.queueTest(test, TestRunFlag.ManualRun or TestRunFlag.NoTestFunc)

            ImGui.popID()
        }
        ImGui.spacing()
        ImGui.popStyleVar()
        ImGui.popStyleVar()
    }
    ImGui.endChild()
}
