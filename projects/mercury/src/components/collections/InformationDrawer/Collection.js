import React from 'react';
import Typography from "@material-ui/core/Typography";
import Icon from "@material-ui/core/Icon";
import {connect} from 'react-redux';
import ErrorDialog from "../../error/ErrorDialog";
import * as collectionActions from '../../../actions/collections';
import CollectionEditor from "../CollectionList/CollectionEditor";
import {findById} from "../../../utils/arrayutils";
import getDisplayName from "../../../utils/userUtils";
import LoadingInlay from '../../generic/Loading/LoadingInlay';
import PermissionChecker from "../../permissions/PermissionChecker";

class Collection extends React.Component {
    constructor(props) {
        super(props);
        this.onDidChangeDetails = props.onDidChangeDetails;

        this.state = {
            collection: props.collection,
            editing: false,
            showEditButton: false
        };
    }

    componentWillReceiveProps(props) {
        this.setState({
            collection: props.collection,
            editing: false
        });
    }

    closeEditDialog = () => {
        this.setState({editing: false});
    }

    handleChangeDetails = (name, description) => {
        this.closeEditDialog();

        if ((name !== this.state.collection.name || description !== this.state.collection.description) && name !== '') {
            this.props.updateCollection(this.state.collection.id, name, description)
                .then(() => {
                    const collection = Object.assign(this.state.collection, {name, description});

                    if (this.onDidChangeDetails) {
                        this.onDidChangeDetails(collection);
                    }
                })
                .catch(e => ErrorDialog.showError(e, "An error occurred while updating collection metadata"));
        }
    }

    handleTextMouseEnter() {
        this.setState(prevState => ({showEditButton: PermissionChecker.canManage(prevState.collection)}));
    }

    handleTextMouseLeave() {
        this.setState({showEditButton: false});
    }

    handleTextClick() {
        this.setState(prevState => ({editing: PermissionChecker.canManage(prevState.collection)}));
    }

    render() {
        const {loading} = this.props;
        if (loading) {
            return <LoadingInlay />;
        }
        return (
            <div>
                <div
                    onClick={this.handleTextClick.bind(this)}
                    onMouseEnter={this.handleTextMouseEnter.bind(this)}
                    onMouseLeave={this.handleTextMouseLeave.bind(this)}
                >
                    <Typography
                        variant="h5"
                        component="h2"
                    >
                        {this.state.collection.name}
                        {' '}
                        {this.state.showEditButton ? (
                            <Icon style={{fontSize: '0.9em'}}>edit</Icon>) : ''}
                    </Typography>
                    <Typography
                        gutterBottom
                        variant="subtitle1"
                        color="textSecondary"
                    >
                        Owner:
                        {` ${this.props.creatorFullname}`}
                    </Typography>
                    <Typography component="p">
                        {this.state.collection.description}
                    </Typography>
                </div>

                <CollectionEditor
                    editing={this.state.editing}
                    name={this.state.collection.name}
                    description={this.state.collection.description}
                    title={`Edit collection: ${this.state.collection.name}`}
                    onSave={this.handleChangeDetails}
                    onCancel={this.closeEditDialog}
                />
            </div>
        );
    }
}

const mapStateToProps = ({cache: {users}}, {collection: {creator}}) => {
    const user = findById(users.data, creator);
    const loading = users.pending || !creator;
    return {
        loading,
        creatorFullname: loading ? '' : getDisplayName(user)
    };
};

const mapDispatchToProps = {
    ...collectionActions
};

export default connect(mapStateToProps, mapDispatchToProps)(Collection);
