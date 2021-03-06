package rcms.fm.gem.gemLevelOne;

import java.util.Date;
import java.util.List;
import java.util.ArrayList;

import rcms.fm.gem.gemLevelOne.util.GEMUtil;
import rcms.fm.gem.gemLevelOne.notificationsToGUI.parameters.ChangedParameterSender;
import rcms.fm.gem.gemLevelOne.updatesFromGUI.GEMSetParameterHandler;
import rcms.fm.gem.gemLevelOne.parameters.GEMParameters;
import rcms.fm.gem.gemLevelOne.parameters.GEMParameterSet;
import rcms.fm.fw.parameter.CommandParameter;
import rcms.fm.fw.parameter.FunctionManagerParameter;
import rcms.fm.fw.parameter.ParameterException;
import rcms.fm.fw.parameter.ParameterSet;
import rcms.fm.fw.parameter.type.IntegerT;
import rcms.fm.fw.parameter.type.StringT;
import rcms.fm.fw.user.UserActionException;
import rcms.fm.fw.user.UserFunctionManager;
import rcms.fm.fw.EventHandlerException;
import rcms.fm.resource.QualifiedGroup;
import rcms.fm.resource.QualifiedResource;
import rcms.fm.resource.QualifiedResourceContainer;
import rcms.fm.resource.qualifiedresource.FunctionManager;
import rcms.fm.resource.qualifiedresource.XdaqApplicationContainer;
import rcms.fm.resource.qualifiedresource.XdaqExecutive;
import rcms.statemachine.definition.State;
import rcms.statemachine.definition.StateMachineDefinitionException;
import rcms.fm.resource.StateVector;
import rcms.fm.resource.StateVectorCalculation;

import rcms.util.logger.RCMSLogger;
import rcms.util.logsession.LogSessionConnector;
import rcms.util.logsession.LogSessionException;

import rcms.utilities.runinfo.RunInfo;


/**
 * Function Machine for controlling the GEM Level 1 Function Manager.
 *
 * @author Andrea Petrucci, Alexander Oh, Michele Gulmini
 * @maintainer Jared Sturdy
 *
 */
public class GEMFunctionManager extends UserFunctionManager {

    /**
     * <code>RCMSLogger</code>: RCMS log4j Logger
     */
    static RCMSLogger logger = new RCMSLogger(GEMFunctionManager.class);

    /**
     * define the xdaq application containers
     */
    public XdaqApplicationContainer containerXdaqApplication  = null;
    public XdaqApplicationContainer containerXdaqExecutive    = null;
    public XdaqApplicationContainer containerGEMSupervisor    = null;
    public XdaqApplicationContainer containerTCDSControl      = null;
    public XdaqApplicationContainer containerGEMRunInfoServer = null;

    /**
     * copied from HCAL, possibly able to incorporate them for use
     * in the GEM system
     */
    public XdaqApplicationContainer containerEVM                 = null;  // maybe only for use with uFEDKIT
    public XdaqApplicationContainer containerBU                  = null;  // maybe only for use with uFEDKIT
    public XdaqApplicationContainer containerRU                  = null;
    public XdaqApplicationContainer containerFUResourceBroker    = null;
    public XdaqApplicationContainer containerFUEventProcessor    = null;
    public XdaqApplicationContainer containerStorageManager      = null;
    public XdaqApplicationContainer containerFEDStreamer         = null;
    public XdaqApplicationContainer containerPeerTransportATCP   = null;

    /**
     * <code>containerFunctionManager</code>: container of FunctionManagers
     * in the running Group.
     */
    public QualifiedResourceContainer containerFunctionManager = null;
    public QualifiedResourceContainer containerJobControl      = null;

    /**
     * <code>calcState</code>: Calculated State.
     */
    public State calcState = null;

    /**
     * <code>svCalc</code>: State vector calculator
     */
    public StateVectorCalculation svCalc = null;

    /**
     * <code>gemParameterSet</code>:
     */
    private GEMParameterSet gemParameterSet;

    // In the template FM we store whether we are degraded in a boolean
    boolean degraded = false;

    // In the template FM we store whether we have detected a softError in a boolean
    boolean softErrorDetected = false;

    private boolean _isDestroyed = false;

    /**
     * This class instance runs periodically and sends updated FM parameters to
     * the GUI. To indicate that a parameter update is required, the method
     * requireParameterUpdate() needs to be called every time a GUI-relevant
     * parameter has changed.
     */
    private ChangedParameterSender changedParameterSender;


    // string containing details on the setup from where this FM was started
    public String RunSetupDetails  = "empty";
    public String FMfullpath       = "empty";
    public String FMname           = "empty";
    public String FMurl            = "empty";
    public String FMuri            = "empty";
    public String FMrole           = "empty";
    public String FMpartition      = "empty";
    public Date   FMtimeofstart;
    public String utcFMtimeofstart = "empty";

    // set from the controlled EventHandler
    public String  RunType = "";
    public Integer RunNumber = 0;
    public Integer CachedRunNumber = 0;

    // connector to log session db, used to create session identifiers
    public LogSessionConnector logSessionConnector;

    // RunInfo stuff:
    // connector to the RunInfo database
    public RunInfo GEMRunInfo        = null;
    public RunInfo GEMRunInfoDESTROY = null;

    // utlitity functions handle
    public GEMUtil GEMUtil;

    // The GEM FED ranges
    protected Boolean GEMin = false;
    protected final Integer firstGEMFedId = 1000;
    protected final Integer lastGEMFedId  = 1010;


    /**
     * Instantiates an GEMFunctionManager.
     */
    public GEMFunctionManager() {
	// Any State Machine Implementation must provide the framework
	// with some information about itself.

        logger.info("[GEMFunctionManager ctor] gemParameterSet:" + this.gemParameterSet);
        this.gemParameterSet = GEMParameterSet.getInstance();
        logger.info("[GEMFunctionManager ctor] gemParameterSet:" + this.gemParameterSet);
	// make the parameters available
	addParameters();
        logger.info("[GEMFunctionManager ctor] gemParameterSet:" + this.gemParameterSet);
    }

    public boolean isDestroyed() {
        return this._isDestroyed;
    }

    public ChangedParameterSender getChangedParameterSender() {
        return this.changedParameterSender;
    }

    @Override
	public GEMParameterSet getParameterSet() {
        return this.gemParameterSet;
    }


    /*
     * (non-Javadoc)
     *
     * @see rcms.statemachine.user.UserStateMachine#createAction()
     * This method is called by the framework when the Function Manager is created.
     */
    @SuppressWarnings("rawtypes")
	@Override
        public void createAction(ParameterSet<CommandParameter> pars) throws UserActionException {
	String message = "[GEMFunctionManager createAction] gemLevelOneFM createAction called.";
	System.out.println(message);
	logger.debug(      message);

	GEMUtil.killOrphanedExecutives();

	message = "[GEMFunctionManager createAction] gemLevelOneFM createAction executed.";
	System.out.println(message);
	logger.debug(      message);
    }

    /*
     * (non-Javadoc)
     * @see rcms.statemachine.user.UserStateMachine#destroyAction()
     * This method is called by the framework when the Function Manager is destroyed.
     */
    @Override
        public void destroyAction() throws UserActionException {
	String message = "[GEMFunctionManager destroyAction] gemLevelOneFM destroyAction called.";

	System.out.println(message);
	logger.debug(      message);

	QualifiedGroup group = getQualifiedGroup();

	List<QualifiedResource> list;

	// destroy XDAQ executives
	list = group.seekQualifiedResourcesOfType(new XdaqExecutive());

	for (QualifiedResource r: list) {
	    logger.debug("[GEMFunctionManager destroyAction] ==== killing " + r.getURI());
	    try {
		((XdaqExecutive)r).killMe();
	    } catch (Exception e) {
		logger.error("[GEMFunctionManager destroyAction] Could not destroy a XDAQ executive " + r.getURI(), e);
	    }
	}

	// destroy function managers
	list = group.seekQualifiedResourcesOfType(new FunctionManager());

	for (QualifiedResource r: list) {
	    logger.debug("[GEMFunctionManager destroyAction] ==== killing " + r.getURI());

	    FunctionManager fm = (FunctionManager)r;

	    if (fm.isInitialized()) {
		try {
		    fm.destroy();
		} catch (Exception e) {
		    logger.error("[GEMFunctionManager destroyAction] Could not destroy a FM " + r.getURI(), e);
		}
	    }
	}

	message = "[GEMFunctionManager destroyAction] gemLevelOneFM destroyAction executed";
	System.out.println(message);
	logger.debug(      message);
    }

    /**
     * add parameters to parameterSet. After this they are accessible.
     */
    private void addParameters() {
	// parameterSet = GEMParameters.LVL_ONE_PARAMETER_SET;
	parameterSet = this.getParameterSet();
    }

    @Override
        public void init() throws StateMachineDefinitionException, EventHandlerException {
        logger.info("[GEMFunctionManager init] starting init()");

	// instantiate utility
	GEMUtil = new GEMUtil(this);
        logger.info("[GEMFunctionManager init] created GEMUtil");

	// Set first of all the State Machine Definition
	setStateMachineDefinition(new GEMStateMachineDefinition());
        logger.info("[GEMFunctionManager init] created GEMUtil");

	// Add event handler
	addEventHandler(new GEMEventHandler());
        logger.info("[GEMFunctionManager init] added event handler GEMEventHandler");

	// add SetParameterHandler
	addEventHandler(new GEMSetParameterHandler());
        logger.info("[GEMFunctionManager init] added event handler GEMSetParameterHandler");

	// Add error handler
	addEventHandler(new GEMErrorHandler());
        logger.info("[GEMFunctionManager init] added event handler GEMErrorHandler");

	// get session ID
	// getSessionId();

	// call renderers
        logger.info("[GEMFunctionManager init] rendering GUI");
	GEMUtil.renderMainGui();
        logger.info("[GEMFunctionManager init] done");
    }

    /**
     * Returns true if custom GUI is required, false otherwise
     * @return true, because GEMFunctionManager class requires user code
     */
    @Override
        public boolean hasCustomGUI() {
	return true;
    }

    public void resetAllParameters() {
        try {
            this.getParameterSet().initializeParameters();
        } catch (ParameterException ex) {
            logger.error("[GEMFunctionManager resetAllParameters] Error reinitializig parameters.", ex);
        }
        this.changedParameterSender.requireParameterUpdate();
    }

    // get a session Id
    @SuppressWarnings("unchecked")
        protected void getSessionId() {
        String user = getQualifiedGroup().getGroup().getDirectory().getUser();
        String description = getQualifiedGroup().getGroup().getDirectory().getFullPath();
        int sessionId = 0;

        logger.debug("[GEMFunctionManager getSessionId] Log session connector: " + logSessionConnector );

        if (logSessionConnector != null) {
            try {
                sessionId = logSessionConnector.createSession( user, description );
                logger.debug("[GEMFunctionManager getSessionId] New session Id obtained =" + sessionId );
            } catch (LogSessionException e1) {
                logger.warn("[GEMFunctionManager getSessionId] Could not get session ID, using default = " +
                            sessionId + ". Exception: ",e1);
            }
        } else {
            logger.warn("[GEMFunctionManager getSessionId] logSessionConnector = " + logSessionConnector +
                        ", using default = " + sessionId + ".");
        }

        // put the session ID into parameter set
        getParameterSet().get(GEMParameters.SID).setValue(new IntegerT(sessionId));
    }

    public boolean isDegraded() {
        // FM may check whether it is currently degraded if such functionality exists
        return degraded;
    }

    public boolean hasSoftError() {
        // FM may check whether the system has a soft error if such functionality exists
        return softErrorDetected;
    }

    // only needed if FM cannot check for degradation
    public void setDegraded(boolean degraded) {
        this.degraded = degraded;
    }

    // only needed if FM cannot check for softError
    public void setSoftErrorDetected(boolean softErrorDetected) {
        this.softErrorDetected = softErrorDetected;
    }
}
