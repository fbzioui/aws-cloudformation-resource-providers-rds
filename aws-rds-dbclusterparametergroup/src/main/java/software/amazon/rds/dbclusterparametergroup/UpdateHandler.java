package software.amazon.rds.dbclusterparametergroup;

import software.amazon.awssdk.services.rds.RdsClient;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.rds.common.handler.Commons;
import software.amazon.rds.common.handler.HandlerConfig;
import software.amazon.rds.common.handler.Tagging;
import software.amazon.rds.common.util.DifferenceUtils;

public class UpdateHandler extends BaseHandlerStd {

    public UpdateHandler() {
        this(DEFAULT_HANDLER_CONFIG);
    }

    public UpdateHandler(final HandlerConfig config) {
        super(config);
    }

    @Override
    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(final AmazonWebServicesClientProxy proxy,
                                                                          final ResourceHandlerRequest<ResourceModel> request,
                                                                          final CallbackContext callbackContext,
                                                                          final ProxyClient<RdsClient> proxyClient,
                                                                          final Logger logger) {
        final ResourceModel model = request.getDesiredResourceState();

        final Tagging.TagSet previousTags = Tagging.TagSet.builder()
                .systemTags(Tagging.translateTagsToSdk(request.getPreviousSystemTags()))
                .stackTags(Tagging.translateTagsToSdk(request.getPreviousResourceTags()))
                .resourceTags(Translator.translateTagsToSdk(request.getPreviousResourceState().getTags()))
                .build();

        final Tagging.TagSet desiredTags = Tagging.TagSet.builder()
                .systemTags(Tagging.translateTagsToSdk(request.getSystemTags()))
                .stackTags(Tagging.translateTagsToSdk(request.getDesiredResourceTags()))
                .resourceTags(Translator.translateTagsToSdk(request.getDesiredResourceState().getTags()))
                .build();

        final boolean shouldUpdateParameters = !DifferenceUtils.diff(request.getPreviousResourceState().getParameters(), request.getDesiredResourceState().getParameters()).isEmpty();

        return ProgressEvent.progress(model, callbackContext)
                .then(progress -> Commons.execOnce(progress, () -> updateTags(proxy, proxyClient, progress, previousTags, desiredTags), CallbackContext::isAddTagsComplete, CallbackContext::setAddTagsComplete))
                .then(progress -> {
                    if (shouldUpdateParameters) {
                        return resetAllParameters(progress, proxy, proxyClient);
                    }
                    return progress;
                }).then(progress -> Commons.execOnce(progress, () -> {
                    if (shouldUpdateParameters) {
                        return applyParameters(proxy, proxyClient, progress.getResourceModel(), progress.getCallbackContext());
                    }
                    return progress;
                }, CallbackContext::isParametersApplied, CallbackContext::setParametersApplied))
                .then(progress -> new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger));
    }
}
