/***
 * Copyright 2013 Teoti Graphix, LLC.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * 
 * @author Michael Schmalle <mschmalle@teotigraphix.com>
 */

package randori.compiler.clients;

import java.io.OutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.flex.compiler.clients.problems.ProblemPrinter;
import org.apache.flex.compiler.clients.problems.ProblemQuery;
import org.apache.flex.compiler.clients.problems.WorkspaceProblemFormatter;
import org.apache.flex.compiler.internal.workspaces.Workspace;
import org.apache.flex.compiler.problems.ICompilerProblem;
import org.apache.flex.compiler.problems.UnexpectedExceptionProblem;
import org.apache.flex.compiler.projects.ICompilerProject;
import org.apache.flex.compiler.units.ICompilationUnit;

import randori.compiler.driver.IBackend;
import randori.compiler.driver.IRandoriBackend;
import randori.compiler.internal.driver.RandoriBackend;
import randori.compiler.internal.projects.RandoriApplicationProject;
import randori.compiler.internal.projects.RandoriBundleProject;
import randori.compiler.internal.projects.RandoriProject;

/**
 * @author Michael Schmalle
 */
public class Randori
{
    private Workspace workspace;

    public Workspace getWorkspace()
    {
        return workspace;
    }

    private RandoriProject project;

    private IBackend backend;

    /**
     * @param args
     */
    public static void main(String[] args)
    {
        final int exitCode = staticMainNoExit(args, null);
        System.exit(exitCode);
    }

    public static int staticMainNoExit(final String[] args,
            Set<ICompilerProblem> problems)
    {
        if (problems == null)
            problems = new HashSet<ICompilerProblem>();
        IBackend backend = new RandoriBackend();
        final Randori randori = new Randori(backend);
        final int exitCode = randori.mainNoExit(args, problems);
        return exitCode;
    }

    public int mainNoExit(final String[] args, Set<ICompilerProblem> problems)
    {
        return mainNoExit(args, System.err, problems);
    }

    public int mainNoExit(String[] args, OutputStream err,
            Set<ICompilerProblem> problems)
    {
        long startTime = System.nanoTime();

        int exitCode = -1;
        try
        {
            exitCode = startCompile(args, problems);
        }
        catch (Exception e)
        {
        }
        finally
        {
            if (problems != null && !problems.isEmpty())
            {
                boolean printProblems = true;
                if (printProblems)
                {
                    final WorkspaceProblemFormatter formatter = new WorkspaceProblemFormatter(
                            workspace);
                    final ProblemPrinter printer = new ProblemPrinter(formatter);
                    printer.printProblems(problems);
                }
            }
        }

        long endTime = System.nanoTime();
        String time = (endTime - startTime) / 1e9 + " seconds";
        System.out.println(time);

        return exitCode;
    }

    private int startCompile(String[] args, Set<ICompilerProblem> outProblems)
    {
        ExitCode exitCode = ExitCode.SUCCESS;
        try
        {
            final boolean continueCompilation = configure(args);

            if (continueCompilation)
            {
                compile();
                if (project.getProblemQuery().hasFilteredProblems())
                    exitCode = ExitCode.FAILED_WITH_PROBLEMS;
            }
            else if (project.getProblemQuery().hasFilteredProblems())
            {
                exitCode = ExitCode.FAILED_WITH_CONFIG_PROBLEMS;
            }
            else
            {
                exitCode = ExitCode.PRINT_HELP;
            }
        }
        catch (Exception e)
        {
            if (outProblems == null)
            {

            }
            else
            {
                final ICompilerProblem unexpectedExceptionProblem = new UnexpectedExceptionProblem(
                        e);
                project.getProblemQuery().add(unexpectedExceptionProblem);
            }
            exitCode = ExitCode.FAILED_WITH_EXCEPTIONS;
        }
        finally
        {
            waitAndClose();
            ProblemQuery query = project.getProblemQuery();
            if (query != null && query.hasFilteredProblems())
            {
                for (ICompilerProblem problem : query.getFilteredProblems())
                {
                    outProblems.add(problem);
                }
            }
        }
        return exitCode.code;
    }

    public Randori(IBackend backend)
    {
        this.backend = backend;
        workspace = new Workspace();
    }

    /**
     * Load configurations from all the sources.
     * 
     * @param args command line arguments
     * @return True if mxmlc should continue with compilation.
     */
    protected boolean configure(final String[] args)
    {
        // hack, output cannot be null
        String output = getRawOutput(args);
        if (output.endsWith(".rbl"))
        {
            project = new RandoriBundleProject(workspace,
                    (IRandoriBackend) backend);
        }
        else
        {
            project = new RandoriApplicationProject(workspace,
                    (IRandoriBackend) backend);
        }
        return project.configure(args);
    }

    protected boolean exportEnabled = false;

    protected boolean compile()
    {
        final String path = project.getTargetSettings().getSDKPath();
        exportEnabled = (path != null && !path.isEmpty())
                || (project instanceof RandoriBundleProject);
        return project.compile(true, exportEnabled);
    }

    /**
     * Wait till the workspace to finish compilation and close.
     */
    protected void waitAndClose()
    {
        workspace.startIdleState();
        try
        {
            workspace.close();
        }
        finally
        {
            workspace.endIdleState(Collections
                    .<ICompilerProject, Set<ICompilationUnit>> emptyMap());
        }
    }

    private String getRawOutput(String[] args)
    {
        for (String arg : args)
        {
            if (arg.startsWith("-output"))
            {
                return arg;
            }
        }
        return null;
    }

    static enum ExitCode
    {
        SUCCESS(0),
        PRINT_HELP(1),
        FAILED_WITH_PROBLEMS(2),
        FAILED_WITH_EXCEPTIONS(3),
        FAILED_WITH_CONFIG_PROBLEMS(4);

        ExitCode(int code)
        {
            this.code = code;
        }

        final int code;
    }
}
