import React from "react";
import PropTypes from "prop-types";

import {Button} from "@material-ui/core";
import LinkedDataShapeChooserDialog from "./LinkedDataShapeChooserDialog";
import {ErrorDialog, ErrorMessage, LoadingInlay, LoadingOverlay} from "../../common";
import LinkedDataList from './LinkedDataList';
import NewLinkedDataEntityDialog from "./NewLinkedDataEntityDialog";

class LinkedDataBrowser extends React.Component {
    static CREATION_STATE_CHOOSE_SHAPE = 'CHOOSE_SHAPE';

    static CREATION_STATE_CREATE_ENTITY = 'CREATE_ENTITY';

    state = {
        shape: null,
        creationState: null
    };

    unMounted = false;

    componentDidMount() {
        this.props.fetchLinkedData();
        this.props.fetchShapes();
    }

    componentWillUnmount() {
        this.unMounted = true;
    }

    startCreating = (e) => {
        e.stopPropagation();

        this.setState({creationState: LinkedDataBrowser.CREATION_STATE_CHOOSE_SHAPE});
    };

    chooseShape = (shape) => {
        this.setState({
            shape,
            creationState: LinkedDataBrowser.CREATION_STATE_CREATE_ENTITY
        });
    };

    closeDialog = (e) => {
        if (e) e.stopPropagation();
        this.setState({creationState: false});
    };

    handleEntityCreation = (formKey, shape, id) => {
        this.setState({creatingMetadataEntity: true});

        this.props.create(formKey, shape, id)
            .then(() => {
                if (!this.unMounted) {
                    this.setState({creatingMetadataEntity: false});
                }
            })
            .catch(e => {
                ErrorDialog.showError(e, `Error creating a new metadata entity.\n${e.message}`);
                if (!this.unMounted) {
                    this.setState({creatingMetadataEntity: false});
                }
            });
    };

    render() {
        const {loading, error, entities} = this.props;

        if (loading) {
            return <LoadingInlay />;
        }

        if (error) {
            return <ErrorMessage message="An error occurred while loading metadata" />;
        }

        return (
            <>
                <Button
                    variant="contained"
                    color="primary"
                    aria-label="Add"
                    title="Create a new metadata entity"
                    onClick={this.startCreating}
                    style={{margin: '10px 0'}}
                    disabled={!this.props.shapes}
                >
                    Create
                </Button>

                <LinkedDataShapeChooserDialog
                    open={this.state.creationState === LinkedDataBrowser.CREATION_STATE_CHOOSE_SHAPE}
                    shapes={this.props.shapes}
                    onChooseShape={this.chooseShape}
                    onClose={this.closeDialog}
                />
                <NewLinkedDataEntityDialog
                    open={this.state.creationState === LinkedDataBrowser.CREATION_STATE_CREATE_ENTITY}
                    linkedData={this.props.vocabulary.emptyLinkedData(this.state.shape)}
                    shape={this.state.shape}
                    onCreate={this.handleEntityCreation}
                    onClose={this.closeDialog}
                    valueComponentFactory={this.props.valueComponentFactory}
                />

                {entities && entities.length > 0 ? <LinkedDataList items={entities} /> : null}
                <LoadingOverlay loading={this.state.creatingMetadataEntity} />
            </>
        );
    }
}

LinkedDataBrowser.propTypes = {
    fetchLinkedData: PropTypes.func.isRequired,
    fetchShapes: PropTypes.func.isRequired,
    create: PropTypes.func.isRequired,

    loading: PropTypes.bool,
    error: PropTypes.bool,
    shapes: PropTypes.array,
    entities: PropTypes.array,

    valueComponentFactory: PropTypes.object.isRequired,
    vocabulary: PropTypes.object.isRequired
};

export default LinkedDataBrowser;
