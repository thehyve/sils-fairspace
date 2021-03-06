import {mount} from "enzyme";
import React from "react";
// eslint-disable-next-line jest/no-mocks-import
import {Checkbox, Slider} from "@material-ui/core";
import FormLabel from "@material-ui/core/FormLabel";
import Input from "@material-ui/core/Input";
import {KeyboardDatePicker} from "@material-ui/pickers";
import Facet from "../MetadataViewFacetFactory";
import TextSelectionFacet from "../facets/TextSelectionFacet";
// eslint-disable-next-line jest/no-mocks-import
import {mockFacets} from "../__mocks__/MetadataViewAPI";
import NumericalRangeSelectionFacet from "../facets/NumericalRangeSelectionFacet";
import DateSelectionFacet from "../facets/DateSelectionFacet";

describe('MetadataViewFacetFactory', () => {
    it('should properly handle invalid facet type', () => {
        const wrapper = mount(<Facet
            title="Facet1"
            options={[]}
            type="unknown_type"
            onChange={() => {}}
            activeFilterValues={[]}
        />);

        expect(wrapper.find(FormLabel).length).toEqual(0);
    });

    it('should render a text selection facet', () => {
        const title = "Gender";
        const options = mockFacets("Subject").find(v => v.title === title).values;
        const wrapper = mount(<Facet
            title={title}
            options={options}
            type="Term"
            onChange={() => {}}
            activeFilterValues={[]}
        />);

        const textSelectionFacet = wrapper.find(TextSelectionFacet);
        expect(textSelectionFacet.length).toEqual(1);
        expect(textSelectionFacet.prop('title')).toEqual(title);

        const facetValues = wrapper.find(Checkbox);
        expect(facetValues.length).toEqual(options.length + 1); // options + "select all" checkbox
        expect(facetValues.at(1).prop('name')).toBe(options[0].value);
        expect(facetValues.at(2).prop('name')).toBe(options[1].value);
        expect(facetValues.at(3).prop('name')).toBe(options[2].value);
    });

    it('should render a numerical range selection facet', () => {
        const title = "Tumor cellularity";
        const mockFacet = mockFacets("Sample").find(v => v.title === title);
        const wrapper = mount(<Facet
            title={title}
            options={[mockFacet.min, mockFacet.max]}
            type="Number"
            onChange={() => {}}
            activeFilterValues={[]}
        />);

        const numericalRangeSelectionFacet = wrapper.find(NumericalRangeSelectionFacet);
        expect(numericalRangeSelectionFacet.length).toEqual(1);
        expect(numericalRangeSelectionFacet.prop('title')).toEqual(title);

        const facetValues = wrapper.find(Input);
        expect(facetValues.length).toEqual(2);
        expect(facetValues.at(0).prop('inputProps').placeholder).toEqual(mockFacet.min);
        expect(facetValues.at(1).prop('inputProps').placeholder).toEqual(mockFacet.max);

        const slider = wrapper.find(Slider);
        expect(slider.length).toEqual(1);
        expect(slider.prop('min')).toEqual(mockFacet.min);
        expect(slider.prop('max')).toEqual(mockFacet.max);
    });

    it('should render a date selection facet', () => {
        const title = "Birth date";
        const mockFacet = mockFacets("Subject").find(v => v.title === title);
        const wrapper = mount(<Facet
            title={title}
            options={[mockFacet.min, mockFacet.max]}
            type="Date"
            onChange={() => {}}
            activeFilterValues={[]}
        />);

        const dateSelectionFacet = wrapper.find(DateSelectionFacet);
        expect(dateSelectionFacet.length).toEqual(1);
        expect(dateSelectionFacet.prop('title')).toEqual(title);

        const pad = (val, n) => `${val}`.padStart(n, '0');
        const formatPlaceholderDate = (d: Date) => `${pad(d.getDate(), 2)}-${pad(d.getMonth() + 1, 2)}-${pad(d.getFullYear(), 4)}`;

        const facetValues = wrapper.find(KeyboardDatePicker);
        expect(facetValues.length).toEqual(2);

        const max = new Date(mockFacet.max.getFullYear(), mockFacet.max.getMonth(), mockFacet.max.getDate(), 23, 59, 59, 999);

        expect(facetValues.at(0).prop('placeholder')).toEqual(formatPlaceholderDate(mockFacet.min));
        expect(facetValues.at(1).prop('placeholder')).toEqual(formatPlaceholderDate(max));
        expect(facetValues.at(0).prop('minDate')).toEqual(mockFacet.min);
        expect(facetValues.at(0).prop('maxDate')).toEqual(max);
        expect(facetValues.at(1).prop('minDate')).toEqual(mockFacet.min);
        expect(facetValues.at(1).prop('maxDate')).toEqual(max);
    });
});
