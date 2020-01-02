import React, {useContext, useEffect} from 'react';
import {Button, Form} from "react-bootstrap";
import {UuidContext} from "../Store";
import {withRouter} from "react-router-dom";
import './UserUUID.css'

export const UserUUID = (props) => {
    const [, setUuid] = useContext(UuidContext);

    useEffect(() => {
        document.title = "Bumpy - Trips"
    });

    return (
        <Form className="centeredForm" onSubmit={(e) => {
            e.preventDefault();

            setUuid(e.target.deviceUUID.value);
            props.history.push('/trips/' + e.target.deviceUUID.value)
        }}>
            <Form.Group controlId="deviceUUID">
                <Form.Label column="">Enter User Identifier</Form.Label>
                <Form.Control className="enterUUID"
                              placeholder="dfsdfg6523-fsdf52s-56s2a"
                              type="text" name="deviceUUID" required/>
                <Form.Text className="text-muted">
                    User Identifier can be found in mobile app under Settings.
                </Form.Text>
                <Button className="btn" type="submit">Enter</Button>
            </Form.Group>
        </Form>
    )
};

export default withRouter(UserUUID)
