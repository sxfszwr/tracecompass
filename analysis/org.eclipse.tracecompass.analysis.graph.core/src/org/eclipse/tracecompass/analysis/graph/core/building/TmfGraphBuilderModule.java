/*******************************************************************************
 * Copyright (c) 2015 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.analysis.graph.core.building;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.graph.core.base.TmfGraph;
import org.eclipse.tracecompass.analysis.graph.core.criticalpath.CriticalPathModule;
import org.eclipse.tracecompass.analysis.graph.core.criticalpath.ICriticalPathProvider;
import org.eclipse.tracecompass.internal.analysis.graph.core.Activator;
import org.eclipse.tracecompass.tmf.core.analysis.TmfAbstractAnalysisModule;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.TmfEvent;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfAnalysisException;
import org.eclipse.tracecompass.tmf.core.request.ITmfEventRequest;
import org.eclipse.tracecompass.tmf.core.request.TmfEventRequest;
import org.eclipse.tracecompass.tmf.core.timestamp.TmfTimeRange;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;

/**
 * Base class for all modules building graphs
 *
 * @author Francis Giraldeau
 * @author Geneviève Bastien
 */
public abstract class TmfGraphBuilderModule extends TmfAbstractAnalysisModule implements ICriticalPathProvider {

    private @Nullable TmfGraph fGraph;
    private @Nullable ITmfEventRequest fRequest;
    private final CriticalPathModule fCriticalPathModule;

    /**
     * Constructor
     */
    public TmfGraphBuilderModule() {
        fCriticalPathModule = new CriticalPathModule(this);
    }

    /**
     * Gets the graph provider to build this graph
     *
     * @return The graph provider
     */
    protected abstract ITmfGraphProvider getGraphProvider();

    /**
     * Gets the graph generated by the analysis
     *
     * @return The generated graph
     */
    public @Nullable TmfGraph getGraph() {
        return fGraph;
    }

    // ------------------------------------------------------------------------
    // TmfAbstractAnalysisModule
    // ------------------------------------------------------------------------

    @Override
    protected boolean executeAnalysis(final IProgressMonitor monitor) {
        if (fGraph == null) {
            final ITmfGraphProvider provider = getGraphProvider();

            /*
             * TODO: This will eventually support multiple backends so we can
             * save the graph on disk, like the state system, but for now, it is
             * just in memory
             */

            createGraph(provider);

        }
        return !monitor.isCanceled();
    }

    @Override
    public boolean setTrace(@NonNull ITmfTrace trace) throws TmfAnalysisException {
        boolean ret = super.setTrace(trace);
        if (!ret) {
            return ret;
        }
        ret = fCriticalPathModule.setTrace(trace);
        return ret;
    }

    @Override
    protected void canceling() {
        ITmfEventRequest req = fRequest;
        if ((req != null) && (!req.isCompleted())) {
            req.cancel();
        }
    }

    @Override
    public void dispose() {
        fCriticalPathModule.dispose();
        super.dispose();
    }

    // ------------------------------------------------------------------------
    // Graph creation methods
    // ------------------------------------------------------------------------

    private void createGraph(ITmfGraphProvider provider) {

        fGraph = new TmfGraph();
        provider.assignTargetGraph(fGraph);

        build(provider);

    }

    private void build(ITmfGraphProvider provider) {
        /* Cancel any previous request */
        ITmfEventRequest request = fRequest;
        if ((request != null) && (!request.isCompleted())) {
            request.cancel();
        }

        try {
            request = new TmfGraphBuildRequest(provider);
            fRequest = request;
            provider.getTrace().sendRequest(request);

            request.waitForCompletion();
        } catch (InterruptedException e) {
            Activator.getInstance().logError("Request interrupted", e); //$NON-NLS-1$
        }
    }

    private static class TmfGraphBuildRequest extends TmfEventRequest {

        private final ITmfGraphProvider fProvider;

        /**
         * Constructor
         *
         * @param provider
         *            The graph provider
         */
        public TmfGraphBuildRequest(ITmfGraphProvider provider) {
            super(TmfEvent.class,
                    TmfTimeRange.ETERNITY,
                    0,
                    ITmfEventRequest.ALL_DATA,
                    ITmfEventRequest.ExecutionType.BACKGROUND);

            fProvider = provider;
        }

        @Override
        public void handleData(final ITmfEvent event) {
            super.handleData(event);
            fProvider.processEvent(event);
        }

        @Override
        public synchronized void done() {
            super.done();
            fProvider.done();
        }

        @Override
        public void handleCancel() {
            fProvider.handleCancel();
            super.handleCancel();
        }

    }

    /**
     * @since 1.1
     */
    @Override
    public @Nullable TmfGraph getCriticalPath() {
        return fCriticalPathModule.getCriticalPath();
    }

}
